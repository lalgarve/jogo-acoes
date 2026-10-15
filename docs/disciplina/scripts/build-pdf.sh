#!/usr/bin/env bash
# Builds the course document's PDF from the Markdown drafts:
#   1. generates the .tex with md2tex.py;
#   2. runs pdflatex, bibtex, pdflatex, pdflatex (references and cross-references);
#   3. shows the LaTeX errors, the page count and the lines wider than the margin.
#
# Needs pdflatex and bibtex with abnTeX2 and Portuguese hyphenation. On Ubuntu/WSL:
#   sudo apt install texlive-latex-extra texlive-publishers texlive-lang-portuguese
#
# Usage (from anywhere): docs/disciplina/scripts/build-pdf.sh
# Output: docs/disciplina/leila-algarve_arquitetura-avancadas-de-software_pd.pdf
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAME="leila-algarve_arquitetura-avancadas-de-software_pd"

for tool in python3 pdflatex bibtex kpsewhich; do
  command -v "$tool" >/dev/null || { echo "Error: $tool not found." >&2; exit 1; }
done
kpsewhich abntex2.cls >/dev/null || {
  echo "Error: abnTeX2 not found (sudo apt install texlive-publishers)." >&2; exit 1; }
kpsewhich brazil.ldf >/dev/null || {
  echo "Error: Portuguese babel not found (sudo apt install texlive-lang-portuguese)." >&2; exit 1; }

cd "$DIR"

echo "==> 1. Generating $NAME.tex"
python3 scripts/md2tex.py

latex() {
  # A LaTeX error doesn't stop the cycle here: the summary below shows it.
  pdflatex -interaction=nonstopmode -file-line-error "$NAME" >/dev/null || true
}

echo "==> 2. Compiling (pdflatex, bibtex, pdflatex, pdflatex)"
latex
# abntex2-options.bib, read by every abntex2cite document, makes bibtex report errors of its
# own (repeated entry, non-ASCII option names); they don't affect the references.
bibtex "$NAME" >/dev/null || true
latex
latex

echo "==> 3. Result"
if grep -aq '^[^ ]*:[0-9]*: ' "$NAME.log"; then
  echo "    LaTeX errors:"
  grep -a -A2 '^[^ ]*:[0-9]*: ' "$NAME.log" | sed 's/^/    /'
fi
if [ ! -f "$NAME.pdf" ]; then
  echo "    no PDF produced; see $DIR/$NAME.log" >&2
  exit 1
fi
grep -a 'Output written' "$NAME.log" | sed 's/^/    /'
echo "    references: $(grep -ac '^\\bibitem' "$NAME.bbl")"
OVERFULL=$(grep -ac '^Overfull \\hbox' "$NAME.log" || true)
echo "    lines wider than the margin: $OVERFULL"
grep -a '^Overfull \\hbox' "$NAME.log" | sed 's/^/      /' || true
echo "    PDF: $DIR/$NAME.pdf"
