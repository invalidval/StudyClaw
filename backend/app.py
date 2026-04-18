from flask import Flask, request, jsonify, g
from flask_cors import CORS
import sqlite3
from werkzeug.security import generate_password_hash, check_password_hash
from db import get_db, init_db

import time

whitelist = ['127.0.0.1', '10.29.50.175', '10.129.247.156','0.0.0.0']  # 允许的IP

app = Flask(__name__)
init_db()
CORS(app)

def parse_user_id_from_auth_header(auth_header):
    if not auth_header:
        return None
    token = auth_header.replace('Bearer ', '').strip()
    if not token.startswith('token_'):
        return None
    try:
        return int(token.split('_', 1)[1])
    except (ValueError, IndexError):
        return None


def safe_int(value, default=0):
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


TOMBSTONE_TTL_MS = 30 * 24 * 60 * 60 * 1000


@app.teardown_appcontext
def close_connection(exception):
    db = getattr(g, '_database', None)
    if db is not None:
        db.close()

@app.route('/api/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    if not username or not password:
        return jsonify({'success': False, 'token': None, 'message': '用户名和密码不能为空'}), 400

    hashed_password = generate_password_hash(password)

    db = get_db()
    cursor = db.cursor()
    try:
        cursor.execute('INSERT INTO users (username, password) VALUES (?, ?)', (username, hashed_password))
        db.commit()
        user_id = cursor.lastrowid
        token = f"token_{user_id}"
        return jsonify({'success': True, 'token': token, 'message': '注册成功', 'user_id': user_id})
    except sqlite3.IntegrityError:
        return jsonify({'success': False, 'token': None, 'message': '用户已存在'}), 409

@app.route('/api/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    if not username or not password:
        return jsonify({'success': False, 'token': None, 'message': '用户名和密码不能为空'}), 400
    db = get_db()
    cursor = db.cursor()
    cursor.execute('SELECT id, password FROM users WHERE username = ?', (username,))
    row = cursor.fetchone()
    if row is None or not check_password_hash(row[1], password):
        return jsonify({'success': False, 'token': None, 'message': '用户名或密码错误'}), 401
    user_id = row[0]
    token = f"token_{user_id}"
    return jsonify({'success': True, 'token': token, 'message': '登录成功', 'user_id': user_id})


@app.route('/api/questions/sync', methods=['POST'])
def sync_questions():
    user_id = parse_user_id_from_auth_header(request.headers.get('Authorization', ''))
    if user_id is None:
        return jsonify({'success': False, 'message': '未授权'}), 401

    data = request.get_json(silent=True) or {}
    incoming_questions = data.get('questions', [])
    if not isinstance(incoming_questions, list):
        return jsonify({'success': False, 'message': '请求格式错误'}), 400

    cursor_updated_at = safe_int(data.get('cursorUpdatedAt'), 0)
    cursor_id = safe_int(data.get('cursorId'), 0)
    limit = safe_int(data.get('limit'), 200)
    if limit <= 0:
        limit = 200
    if limit > 1000:
        limit = 1000

    db = get_db()
    cursor = db.cursor()

    visible_updated_count = 0

    for q in incoming_questions:
        if not isinstance(q, dict):
            continue
        cloud_id = q.get('id')
        updated_at = safe_int(q.get('updatedAt'), 0)
        image_path = q.get('imagePath', '')
        ocr_text = (q.get('ocrText') or '').strip()
        ai_analysis = q.get('aiAnalysis', '')
        summary = (q.get('summary') or '').strip()
        subject = (q.get('subject') or '').strip()
        difficulty = safe_int(q.get('difficulty'), 1)
        create_time = safe_int(q.get('createTime'), updated_at)
        is_archived = 1 if q.get('isArchived') else 0
        archive_type = q.get('archiveType')
        deleted_at_raw = safe_int(q.get('deletedAt'), 0)
        deleted_at = deleted_at_raw if deleted_at_raw > 0 else None

        # Keep sync permissive: only OCR text is required, and fill other fields with defaults.
        if not ocr_text:
            continue
        if not summary:
            summary = ocr_text[:20]
        if not subject:
            subject = '未分类'

        if cloud_id:
            cursor.execute(
                'SELECT updated_at, deleted_at FROM questions WHERE id = ? AND user_id = ?',
                (cloud_id, user_id)
            )
            existing = cursor.fetchone()
            if existing is not None:
                remote_updated_at = int(existing[0] or 0)
                remote_deleted_at = safe_int(existing[1], 0)
                incoming_updated_at = updated_at if updated_at > 0 else create_time
                # On equal timestamps, keep delete to avoid accidental resurrection.
                same_time_keep_remote_delete = (
                    incoming_updated_at == remote_updated_at and
                    remote_deleted_at > 0 and
                    deleted_at is None
                )

                # Idempotent sync: equal timestamp means same version, skip recount/rewrite.
                if incoming_updated_at <= remote_updated_at or same_time_keep_remote_delete:
                    continue

                if incoming_updated_at > remote_updated_at:
                    cursor.execute(
                        '''
                        UPDATE questions
                        SET image_path = ?, ocr_text = ?, ai_analysis = ?, summary = ?,
                            subject = ?, difficulty = ?, create_time = ?, is_archived = ?,
                            archive_type = ?, deleted_at = ?, updated_at = ?
                        WHERE id = ? AND user_id = ?
                        ''',
                        (
                            image_path,
                            ocr_text,
                            ai_analysis,
                            summary,
                            subject,
                            difficulty,
                            create_time,
                            is_archived,
                            archive_type,
                            deleted_at,
                            incoming_updated_at,
                            cloud_id,
                            user_id,
                        )
                    )
                    # User-facing count only tracks visible (non-deleted) updates.
                    if deleted_at is None:
                        visible_updated_count += 1
                continue

            # cloud_id exists in payload but does not belong to current user.
            # Skip to avoid cross-account duplication when switching accounts.
            continue

        cursor.execute(
            '''
            INSERT INTO questions (
                user_id, image_path, ocr_text, ai_analysis, summary, subject,
                difficulty, create_time, is_archived, archive_type, deleted_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''',
            (
                user_id,
                image_path,
                ocr_text,
                ai_analysis,
                summary,
                subject,
                difficulty,
                create_time,
                is_archived,
                archive_type,
                deleted_at,
                updated_at if updated_at > 0 else create_time,
            )
        )
        if deleted_at is None:
            visible_updated_count += 1

    # Purge old tombstones to keep cloud data bounded.
    now_ms = int(time.time() * 1000)
    purge_before = now_ms - TOMBSTONE_TTL_MS
    cursor.execute(
        'DELETE FROM questions WHERE user_id = ? AND deleted_at IS NOT NULL AND deleted_at < ?',
        (user_id, purge_before)
    )

    db.commit()

    cursor.execute(
        '''
        SELECT id, image_path, ocr_text, ai_analysis, summary, subject, difficulty,
               create_time, is_archived, archive_type, deleted_at, updated_at
        FROM questions
        WHERE user_id = ?
          AND (
                updated_at > ?
                OR (updated_at = ? AND id > ?)
              )
        ORDER BY updated_at ASC, id ASC
        LIMIT ?
        ''',
        (user_id, cursor_updated_at, cursor_updated_at, cursor_id, limit)
    )
    rows = cursor.fetchall()
    questions = [
        {
            'id': row[0],
            'imagePath': row[1],
            'ocrText': row[2],
            'aiAnalysis': row[3],
            'summary': row[4],
            'subject': row[5],
            'difficulty': row[6],
            'createTime': row[7],
            'isArchived': bool(row[8]),
            'archiveType': row[9],
            'deletedAt': row[10],
            'updatedAt': row[11],
        }
        for row in rows
    ]
    next_cursor_updated_at = cursor_updated_at
    next_cursor_id = cursor_id
    if rows:
        last = rows[-1]
        next_cursor_updated_at = safe_int(last[11], cursor_updated_at)
        next_cursor_id = safe_int(last[0], cursor_id)

    cursor.execute(
        '''
        SELECT 1
        FROM questions
        WHERE user_id = ?
          AND (
                updated_at > ?
                OR (updated_at = ? AND id > ?)
              )
        LIMIT 1
        ''',
        (user_id, next_cursor_updated_at, next_cursor_updated_at, next_cursor_id)
    )
    has_more = cursor.fetchone() is not None

    return jsonify({
        'success': True,
        'questions': questions,
        'message': f'同步成功：更新 {visible_updated_count} 条',
        'stats': {
            'updatedCount': visible_updated_count,
        },
        'cursor': {
            'updatedAt': next_cursor_updated_at,
            'id': next_cursor_id,
            'hasMore': has_more,
        },
    })

@app.route('/api/questions/<int:question_id>', methods=['GET'])
def get_question(question_id):
    db = get_db()
    cursor = db.cursor()
    cursor.execute(
        '''
        SELECT id, image_path, ocr_text, ai_analysis, summary, subject, difficulty,
               create_time, is_archived, archive_type, deleted_at, updated_at
        FROM questions
        WHERE id = ? AND deleted_at IS NULL
        ''',
        (question_id,)
    )
    row = cursor.fetchone()
    if row is None:
        return jsonify({'success': False, 'message': '题目不存在'}), 404

    question = {
        'id': row[0],
        'imagePath': row[1],
        'ocrText': row[2],
        'aiAnalysis': row[3],
        'summary': row[4],
        'subject': row[5],
        'difficulty': row[6],
        'createTime': row[7],
        'isArchived': bool(row[8]),
        'archiveType': row[9],
        'deletedAt': row[10],
        'updatedAt': row[11],
    }
    return jsonify({'success': True, 'question': question})

@app.route('/api/heartbeat', methods=['GET'])
def heartbeat():
    client_ip = request.remote_addr
   
    if client_ip in whitelist:
        return jsonify({'success': True, 'message': 'Heartbeat OK'})
    else:
        return jsonify({'success': False, 'message': 'IP not in whitelist'}), 403

@app.route('/api/check_ip', methods=['GET'])
def check_ip():
    client_ip = request.remote_addr
    # PRE模式下的白名单IP列表
   
    if client_ip in whitelist:
        return jsonify({'allowed': True, 'message': 'IP allowed'})
    else:
        return jsonify({'allowed': False, 'message': 'IP not in whitelist'}), 403

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=True)
