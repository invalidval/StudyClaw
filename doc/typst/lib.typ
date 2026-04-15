#import "@preview/cuti:0.3.0": show-cn-fakebold
#import "cover.typ": generate-cover
#show: show-cn-fakebold

#let experiment-report(
  row1: "",
  row2: "",
  lab: "",
  name: "",
  student-id: "",
  class: "",
  date: "",
  body,
) = {
  
  // 初始化相关页面、文本和段落样式
  set page(
    paper: "a4",
    margin: (top: 2.54cm, bottom: 2.54cm, left: 3.18cm, right: 3.18cm),
    footer: context {
    if counter(page).get().first() > 1 {
      align(center)[#counter(page).display("1")]
    }
    },
  )
  set text(
    font: ("Calibri", "SimSun"),
    size: 12pt,
    weight: "regular",
    lang: "zh"//按中文方式排版断句
  )
  set par(first-line-indent: (amount:2em,all:true), leading: 1em)//首行缩进2字符，行距1倍
  set heading(numbering: "1.")//启用层级编号：1、1.1、1.1.1
  
  // 封面页
  generate-cover(
    row1,
    row2,
    lab,
    class,
    name,
    student-id,
    date
  )

  // 图表标题样式
  show figure.caption: it => {
    text(font: ("Calibri", "SimSun"), size: 9pt)[#it.body]
  }

  // 一级标题样式
  show heading.where(level: 1): it => [
    #align(left)[
      #par(first-line-indent: (amount:0em,all:true))[
        #text(font: ("Calibri", "SimSun"), size: 22pt, weight: "bold")[
          #if it.numbering != none {
            let nums = counter(heading).at(it.location())
            numbering("1. ", nums.at(0))
          }
          #it.body
        ]
      ]
    ]

  ]

  // 二级标题样式
  show heading.where(level: 2): it => [
    #align(left)[
      #par(first-line-indent: (amount:0em,all:true))[
        #text(font: ("Calibri", "SimSun"), size: 16pt, weight: "bold")[
          #if it.numbering != none {
            let nums = counter(heading).at(it.location())
            numbering("1 ", nums.at(1))
          }
          #it.body
        ]
      ]
    ]
  ]

  // 三级标题样式
  show heading.where(level: 3): it => [
    #align(left)[
      #par(first-line-indent: (amount:0em,all:true))[
        #text(font: ("Calibri", "SimSun"), size: 12pt, weight: "bold")[
          #if it.numbering != none {
            let nums = counter(heading).at(it.location())
            numbering("1.1 ", nums.at(1), nums.at(2))
          }
          #it.body
        ]
      ]
    ]
  ]

  // 代码标题样式
  let code-with-lines(code, lang: none) = {
    let lines = code.text.trim().split("\n")
    let numbered = lines.enumerate().map(it => {
      let (idx, line) = it
      let num = str(idx + 1)
      let padded = if num.len() < 2 { "0" + num } else { num }
      padded + "  " + line
    }).join("\n")
    raw(numbered, lang: lang)
  }

  show raw.where(block: true): elem => [
    #table(
      columns: (1fr,),
      [
        #block(width: 100%)[
          #text(size: 10pt)[
            #code-with-lines(elem, lang: elem.lang)
          ]
        ]
      ],
    )
  ]


  // 主体文档
  body
}

#let styled-parameter-table(cols: none, ..args) = {
  set table.cell(inset: (x: 6pt, y: 5pt))

  let default-cols = if cols != none {
    cols
  } else {
    (18%, 82%)
  }

  table(
    columns: default-cols,
    stroke: (x, y) => 0.6pt + rgb("#b7c3d0"),
    fill: (x, y) => {
      if y == 0 {
        rgb("#366d98")
      } else if calc.odd(y) {
        white
      } else {
        rgb("#edf2f8")
      }
    },
    align: (x, y) => {
      if y == 0 or x == 0 {
        center + horizon
      } else {
        left + horizon
      }
    },
    ..args,
  )
}
