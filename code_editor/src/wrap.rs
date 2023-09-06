use crate::{
    layout::{InlineElement, Line},
    str::StrExt,
};

pub fn wrap(
    line: Line<'_>,
    max_column_count: usize,
    tab_column_count: usize,
    positions: &mut Vec<usize>,
) -> usize {
    let mut indentation_width: usize = line
        .text
        .leading_whitespace()
        .unwrap_or("")
        .column_count(tab_column_count);
    for inline in line.inline_elements() {
        match inline {
            InlineElement::Text { text, .. } => {
                for string in text.split_whitespace_boundaries() {
                    let column_count: usize = string.column_count(tab_column_count);
                    if indentation_width + column_count > max_column_count {
                        indentation_width = 0;
                        break;
                    }
                }
            }
            InlineElement::Widget(widget) => {
                if indentation_width + widget.column_count > max_column_count {
                    indentation_width = 0;
                    break;
                }
            }
        }
    }
    let mut position = 0;
    let mut column_index = 0;
    for element in line.inline_elements() {
        match element {
            InlineElement::Text { text, .. } => {
                for string in text.split_whitespace_boundaries() {
                    let column_count: usize = string.column_count(tab_column_count);
                    if column_index + column_count > max_column_count {
                        column_index = indentation_width;
                        positions.push(position);
                    }
                    column_index += column_count;
                    position += string.len();
                }
            }
            InlineElement::Widget(widget) => {
                if column_index + widget.column_count > max_column_count {
                    column_index = indentation_width;
                    positions.push(position);
                }
                column_index += widget.column_count;
                position += 1;
            }
        }
    }
    indentation_width
}
