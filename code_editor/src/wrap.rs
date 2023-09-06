use crate::{
    char::CharExt,
    layout::{InlineElement, Line},
    str::StrExt,
};

pub fn wrap(
    line: Line<'_>,
    max_width: usize,
    tab_width: usize,
    positions: &mut Vec<usize>,
) -> usize {
    let mut indentation_width: usize = line
        .text
        .indentation()
        .unwrap_or("")
        .chars()
        .map(|char| char.width(tab_width))
        .sum();
    for inline in line.inline_elements() {
        match inline {
            InlineElement::Text { text, .. } => {
                for string in text.split_whitespace_boundaries() {
                    let width: usize = string.chars().map(|char| char.width(tab_width)).sum();
                    if indentation_width + width > max_width {
                        indentation_width = 0;
                        break;
                    }
                }
            }
            InlineElement::Widget(widget) => {
                if indentation_width + widget.width > max_width {
                    indentation_width = 0;
                    break;
                }
            }
        }
    }
    let mut position = 0;
    let mut total_width = 0;
    for element in line.inline_elements() {
        match element {
            InlineElement::Text { text, .. } => {
                for string in text.split_whitespace_boundaries() {
                    let width: usize = string.chars().map(|char| char.width(tab_width)).sum();
                    if total_width + width > max_width {
                        total_width = indentation_width;
                        positions.push(position);
                    }
                    total_width += width;
                    position += string.len();
                }
            }
            InlineElement::Widget(widget) => {
                if total_width + widget.width > max_width {
                    total_width = indentation_width;
                    positions.push(position);
                }
                total_width += widget.width;
                position += 1;
            }
        }
    }
    indentation_width
}
