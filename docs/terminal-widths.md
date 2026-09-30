# Terminal widths

The character tables use Unicode 17.0.0:

- [EastAsianWidth.txt](https://www.unicode.org/Public/17.0.0/ucd/EastAsianWidth.txt): Wide and Fullwidth ranges use two columns.
- [DerivedGeneralCategory.txt](https://www.unicode.org/Public/17.0.0/ucd/extracted/DerivedGeneralCategory.txt): Nonspacing and Enclosing Mark ranges use zero columns, even when their width property is Wide.

Other characters use one column. Ambiguous width defaults to one. These tables
do not determine the width of an entire emoji sequence.

TerminalWidth merges adjacent ranges and searches their endpoints. The test
fixture keeps the source ranges unmerged and compares every Unicode code point.

The table is already used for combining marks, so older Android versions recognise
the same marks. Two-column grid placement and editing are not implemented yet.

The data is covered by [Unicode License V3](../licenses/Unicode.txt). The same
notice is included in the app's assets.
