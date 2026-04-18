#let generate-cover(row1, row2, lab, class, name, student-id, date) = {
  // 封面页
  align(center)[
    // 第一行：学年学期
    #v(2em) // 距离页面顶部 2em
    #text(size: 28pt, font: ("Calibri", "SimSun"), weight: "bold")[#row1]
    #v(2em) // 行距
    // 第二行：实验报告标题
    #text(size: 26pt, font: ("Calibri", "SimSUn"), weight: "bold")[#row2]

    // Logo
    // #image("./assets/bupt-scs.png", width: 80%)
    

    // 实验名称
    #v(5em) // 行距
    
    #grid(
      align: center,
      columns: 2,
      column-gutter: 2em,
      [
      #text(font: ("Calibri", "SimSun"), weight: "bold", size: 24pt)[题目:]
      ],

      [#box(width: 500pt)[
        
        #line(length: 360pt)
        #v(-2em)  // 调整文字位置
        #place(
          dx: 6em ,
          dy: 0em,
          text(font: ("Calibri", "SimSun"),weight: "bold", size: 24pt)[#lab]
        ) 
      ]
      ]
    )

    #v(5em)
    #align(center)[
      #grid(
      columns: 2,
      column-gutter: 1em,
      align: center,
      [
        #text(font: ("Calibri", "SimSun"), weight: "bold", size: 16pt)[类型:]
      ],
      [
        #underline(
          offset: 3pt,  // 调整下划线与文字的间距
          text(font: ("Calibri", "SimSun"), weight: "bold", size: 16pt)[应用系统设计实现]
        )
      ]
      )
    ]
    


    #v(6em)
    // 学生信息表格
    #let info-row(name,class,number) = {
      
      table(
        columns: (110pt,120pt,130pt),
        inset: 10pt,
        align: left,
        table.header(
          [
          #text(font: ("Calibri", "SimSun"),weight: "bold", size: 14pt)[*姓名*]
          ],
          [
          #text(font: ("Calibri", "SimSun"),weight: "bold", size: 14pt)[*班级*]
          ],
          [
          #text(font: ("Calibri", "SimSun"),weight: "bold", size: 14pt)[*学号*]
          ],
          
        ),
        [
          #text(font: ("Calibri", "SimSun"), size: 14pt)[#name]
        ],
        [
          #text(font: ("Calibri", "SimSun"), size: 14pt)[#class]
        ],
        [
          #text(font: ("Calibri", "SimSun"), size: 14pt)[#student-id]
        ],
          
          
      )
    }
    
    #grid(
      align: center,
      rows: 4,
      row-gutter: 2em,
      info-row(name, class, student-id),
    )
    
  ]
  v(1fr)       // 占掉剩余所有竖直空间       
  align(center)[
    #text(font: ("Calibri", "SimSun"), weight: "bold", size: 18pt)[#date]
  ]
  v(0em) // 距离内容区域底部 2em
  pagebreak()

  // 目录样式设置
  show outline.entry.where(level: 1): it => {
    set text(font: ("Calibri","SimSun"), size: 14pt)
    it
  }
  show outline.entry.where(level: 2): it => {
    set text(font: ("Calibri","SimSun"),size: 12pt)
    it
  }
  show outline.entry.where(level: 3): it => {
    set text(font: ("Calibri","SimSun"),size: 11pt)
    it
  }

  // 目录
  align(center)[
      #text(fill: rgb("#000000"), size: 22pt, weight: 700)[目录]
    ]
  outline(
    title: none,
    indent: 2em,
    depth: 3
  )




  pagebreak()
}
