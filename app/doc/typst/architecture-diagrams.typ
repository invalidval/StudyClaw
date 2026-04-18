#let clos-switch-node(title, subtitle, fill-color: rgb("#eef4fb"), stroke-color: rgb("#7f9db9")) = {
  rect(
    width: 100%,
    inset: 8pt,
    radius: 6pt,
    fill: fill-color,
    stroke: 0.9pt + stroke-color,
  )[
    #align(center + horizon)[
      #text(weight: "bold")[#title]
      #par(first-line-indent: (amount: 0em, all: true), leading: 0.4em)[
        #text(size: 9pt, fill: rgb("#44566c"))[#subtitle]
      ]
    ]
  ]
}

#let clos-link(label) = {
  box(width: 2.2cm, height: 1.9cm)[
    #place(dx: 0pt, dy: 0.75cm)[
      #line(length: 2.2cm, stroke: 1.1pt + rgb("#8ba3ba"))
    ]
    #place(dx: 2.2cm, dy: 0.75cm)[
      #line(length: 0.18cm, angle: 150deg, stroke: 1.1pt + rgb("#8ba3ba"))
    ]
    #place(dx: 2.2cm, dy: 0.75cm)[
      #line(length: 0.18cm, angle: 210deg, stroke: 1.1pt + rgb("#8ba3ba"))
    ]
    #place(dx: 0pt, dy: 1.32cm)[
      #align(center + horizon)[
        #text(size: 9pt, fill: rgb("#44566c"))[#label]
      ]
    ]
  ]
}

#let clos-network-diagram() = {
  align(center)[
    #grid(
      columns: (2.7cm, 2.2cm, 2.7cm, 2.2cm, 2.7cm),
      rows: (auto, auto, auto, auto),
      column-gutter: 6pt,
      row-gutter: 9pt,

      [#clos-switch-node($I_1$, $m_1 times n_1$)],
      [#clos-link($n_1 = r_2$)],
      [#clos-switch-node($M_1$, $r_1 times r_3$, fill-color: rgb("#f6f1e8"), stroke-color: rgb("#b89b68"))],
      [#clos-link($m_3 = r_2$)],
      [#clos-switch-node($O_1$, $m_3 times n_3$, fill-color: rgb("#eef7ec"), stroke-color: rgb("#7ca271"))],

      [#align(center)[#text(size: 14pt, fill: rgb("#6c7a89"))[⋮]]],
      [#align(center)[#text(size: 14pt, fill: rgb("#6c7a89"))[⋯]]],
      [#align(center)[#text(size: 14pt, fill: rgb("#6c7a89"))[⋮]]],
      [#align(center)[#text(size: 14pt, fill: rgb("#6c7a89"))[⋯]]],
      [#align(center)[#text(size: 14pt, fill: rgb("#6c7a89"))[⋮]]],

      [#clos-switch-node($I_(r_1)$, $m_1 times n_1$)],
      [#clos-link([全互连])],
      [#clos-switch-node($M_(r_2)$, $r_1 times r_3$, fill-color: rgb("#f6f1e8"), stroke-color: rgb("#b89b68"))],
      [#clos-link([全互连])],
      [#clos-switch-node($O_(r_3)$, $m_3 times n_3$, fill-color: rgb("#eef7ec"), stroke-color: rgb("#7ca271"))],

      [#align(center)[#text(size: 9pt, weight: "bold", fill: rgb("#2f5b8a"))[
        输入级
        #linebreak()
        共 #math.equation(block: false, $r_1$) 个单元
      ]]],
      [],
      [#align(center)[#text(size: 9pt, weight: "bold", fill: rgb("#8a6a2f"))[
        中间级
        #linebreak()
        共 #math.equation(block: false, $r_2$) 个单元
      ]]],
      [],
      [#align(center)[#text(size: 9pt, weight: "bold", fill: rgb("#44704a"))[
        输出级
        #linebreak()
        共 #math.equation(block: false, $r_3$) 个单元
      ]]],
    )
  ]
}

#let spine-leaf-diagram() = {
  align(center)[
    #grid(
      columns: (3.1cm, 1.2cm, 3.1cm),
      rows: (auto, auto, auto, auto, auto),
      column-gutter: 10pt,
      row-gutter: 8pt,

      [#align(center)[#text(size: 10pt, weight: "bold", fill: rgb("#2f5b8a"))[Leaf 层]]],
      [],
      [#align(center)[#text(size: 10pt, weight: "bold", fill: rgb("#8a6a2f"))[Spine 层]]],

      [#clos-switch-node($L_1$, [接入服务器与终端], fill-color: rgb("#dbe8f8"), stroke-color: rgb("#7f9db9"))],
      [#align(center + horizon)[#text(size: 16pt, fill: rgb("#5c7085"))[↔]]],
      [#clos-switch-node($S_1$, [高速转发骨干], fill-color: rgb("#f6f1e8"), stroke-color: rgb("#b89b68"))],

      [#align(center)[#text(size: 14pt, fill: rgb("#6c7a89"))[⋮]]],
      [#align(center + horizon)[#text(size: 16pt, fill: rgb("#5c7085"))[↔]]],
      [#align(center)[#text(size: 14pt, fill: rgb("#6c7a89"))[⋮]]],

      [#clos-switch-node($L_n$, [下联服务器 / 存储], fill-color: rgb("#dbe8f8"), stroke-color: rgb("#7f9db9"))],
      [#align(center + horizon)[#text(size: 16pt, fill: rgb("#5c7085"))[↔]]],
      [#clos-switch-node($S_k$, [仅承担 Leaf 间转发], fill-color: rgb("#f6f1e8"), stroke-color: rgb("#b89b68"))],

      grid.cell(colspan: 3)[
        #align(center)[
          #text(size: 9pt, fill: rgb("#6c7a89"))[标准全互联]
        ]
      ],
    )
  ]
}

= 架构图参考

#figure(
  clos-network-diagram(),
  caption: [一般三级 Clos 交换网络的结构示意]
)

#figure(
  spine-leaf-diagram(),
  caption: [Spine-Leaf 架构左右两列拓扑示意]
)
