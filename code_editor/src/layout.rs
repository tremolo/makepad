use {
    crate::{
        state::{BlockInlay, BlockWidget, InlineInlay, InlineWidget},
        text::Text,
    },
    std::slice::Iter,
};

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Layout<'a> {
    pub y: &'a [f64],
    pub column_count: &'a [usize],
    pub fold_column_index: &'a [usize],
    pub fold_scale: &'a [f64],
    pub text: &'a Text,
    pub inline_inlays: &'a [Vec<(usize, InlineInlay)>],
    pub wrap_byte_indices: &'a [Vec<usize>],
    pub wrap_indentation_width: &'a [usize],
    pub block_inlays: &'a [(usize, BlockInlay)],
}

impl<'a> Layout<'a> {
    pub fn as_text(&self) -> &Text {
        self.text
    }

    pub fn line_count(&self) -> usize {
        self.text.as_lines().len()
    }

    pub fn find_first_line_ending_after_y(&self, y: f64) -> usize {
        match self.y[..self.y.len() - 1]
            .binary_search_by(|current_y| current_y.partial_cmp(&y).unwrap())
        {
            Ok(line) => line,
            Err(line) => line.saturating_sub(1),
        }
    }

    pub fn find_first_line_starting_after_y(&self, y: f64) -> usize {
        match self.y[..self.y.len() - 1]
            .binary_search_by(|current_y| current_y.partial_cmp(&y).unwrap())
        {
            Ok(line) => line + 1,
            Err(line) => line,
        }
    }

    pub fn line(&self, index: usize) -> Line<'_> {
        Line {
            y: self.y.get(index).copied().unwrap_or(0.0),
            column_count: self.column_count[index],
            fold_column_index: self.fold_column_index[index],
            fold_scale: self.fold_scale[index],
            text: &self.text.as_lines()[index],
            inlays: &self.inline_inlays[index],
            wrap_byte_indices: &self.wrap_byte_indices[index],
            wrap_indentation_width: self.wrap_indentation_width[index],
        }
    }

    pub fn lines(&self, start: usize, end: usize) -> Lines<'_> {
        Lines {
            y: self.y[start.min(self.y.len())..end.min(self.y.len())].iter(),
            column_count: self.column_count[start..end].iter(),
            fold_column_index: self.fold_column_index[start..end].iter(),
            fold_scale: self.fold_scale[start..end].iter(),
            text: self.text.as_lines()[start..end].iter(),
            inlays: self.inline_inlays[start..end].iter(),
            wrap_byte_indices: self.wrap_byte_indices[start..end].iter(),
            wrap_indentation_width: self.wrap_indentation_width[start..end].iter(),
        }
    }

    pub fn block_elements(&self, line_start: usize, line_end: usize) -> BlockElements<'_> {
        let mut inlays = self.block_inlays.iter();
        while inlays
            .as_slice()
            .first()
            .map_or(false, |&(index, _)| index < line_start)
        {
            inlays.next();
        }
        BlockElements {
            lines: self.lines(line_start, line_end),
            inlays,
            line_index: line_start,
        }
    }
}

#[derive(Clone, Debug)]
pub struct Lines<'a> {
    pub y: Iter<'a, f64>,
    pub column_count: Iter<'a, usize>,
    pub fold_column_index: Iter<'a, usize>,
    pub fold_scale: Iter<'a, f64>,
    pub text: Iter<'a, String>,
    pub inlays: Iter<'a, Vec<(usize, InlineInlay)>>,
    pub wrap_byte_indices: Iter<'a, Vec<usize>>,
    pub wrap_indentation_width: Iter<'a, usize>,
}

impl<'a> Iterator for Lines<'a> {
    type Item = Line<'a>;

    fn next(&mut self) -> Option<Self::Item> {
        Some(Line {
            y: self.y.next().copied().unwrap_or(0.0),
            column_count: *self.column_count.next()?,
            fold_column_index: *self.fold_column_index.next()?,
            fold_scale: *self.fold_scale.next()?,
            text: self.text.next()?,
            inlays: self.inlays.next()?,
            wrap_byte_indices: self.wrap_byte_indices.next()?,
            wrap_indentation_width: *self.wrap_indentation_width.next()?,
        })
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Line<'a> {
    pub y: f64,
    pub column_count: usize,
    pub fold_column_index: usize,
    pub fold_scale: f64,
    pub text: &'a str,
    pub inlays: &'a [(usize, InlineInlay)],
    pub wrap_byte_indices: &'a [usize],
    pub wrap_indentation_width: usize,
}

impl<'a> Line<'a> {
    pub fn row_count(&self) -> usize {
        self.wrap_byte_indices.len() + 1
    }

    pub fn height(&self) -> f64 {
        self.row_count() as f64 * self.fold_scale
    }

    pub fn inline_elements(&self) -> InlineElements<'_> {
        InlineElements {
            text: self.text,
            inlays: self.inlays.iter(),
            byte_index: 0,
        }
    }

    pub fn wrapped_elements(&self) -> WrappedElements<'_> {
        let mut elements = self.inline_elements();
        WrappedElements {
            element: elements.next(),
            elements,
            wrap_byte_indices: self.wrap_byte_indices.iter(),
            byte_index: 0,
        }
    }
}

#[derive(Clone, Debug)]
pub struct InlineElements<'a> {
    text: &'a str,
    inlays: Iter<'a, (usize, InlineInlay)>,
    byte_index: usize,
}

impl<'a> Iterator for InlineElements<'a> {
    type Item = InlineElement<'a>;

    fn next(&mut self) -> Option<Self::Item> {
        if self
            .inlays
            .as_slice()
            .first()
            .map_or(false, |&(index, _)| index == self.byte_index)
        {
            let (_, inline_inlay) = self.inlays.next().unwrap();
            return Some(match *inline_inlay {
                InlineInlay::Text(ref text) => InlineElement::Text {
                    is_inlay: true,
                    text,
                },
                InlineInlay::Widget(widget) => InlineElement::Widget(widget),
            });
        }
        if self.text.is_empty() {
            return None;
        }
        let mut len = self.text.len();
        if let Some(&(byte_index, _)) = self.inlays.as_slice().first() {
            len = len.min(byte_index - self.byte_index);
        }
        let (text, remaining_text) = self.text.split_at(len);
        self.text = remaining_text;
        self.byte_index += text.len();
        Some(InlineElement::Text {
            is_inlay: false,
            text,
        })
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum InlineElement<'a> {
    Text { is_inlay: bool, text: &'a str },
    Widget(InlineWidget),
}

#[derive(Clone, Debug)]
pub struct WrappedElements<'a> {
    element: Option<InlineElement<'a>>,
    elements: InlineElements<'a>,
    wrap_byte_indices: Iter<'a, usize>,
    byte_index: usize,
}

impl<'a> Iterator for WrappedElements<'a> {
    type Item = WrappedElement<'a>;

    fn next(&mut self) -> Option<Self::Item> {
        if self
            .wrap_byte_indices
            .as_slice()
            .first()
            .map_or(false, |&byte_index| byte_index == self.byte_index)
        {
            self.wrap_byte_indices.next();
            return Some(WrappedElement::Wrap);
        }
        Some(match self.element.take()? {
            InlineElement::Text { is_inlay, text } => {
                let mut len = text.len();
                if let Some(&index) = self.wrap_byte_indices.as_slice().first() {
                    len = len.min(index - self.byte_index);
                }
                let text = if len < text.len() {
                    let (text, remaining_text) = text.split_at(len);
                    self.element = Some(InlineElement::Text {
                        is_inlay,
                        text: remaining_text,
                    });
                    text
                } else {
                    self.element = self.elements.next();
                    text
                };
                self.byte_index += text.len();
                WrappedElement::Text { is_inlay, text }
            }
            InlineElement::Widget(widget) => {
                self.byte_index += 1;
                WrappedElement::Widget(widget)
            }
        })
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum WrappedElement<'a> {
    Text { is_inlay: bool, text: &'a str },
    Widget(InlineWidget),
    Wrap,
}

#[derive(Clone, Debug)]
pub struct BlockElements<'a> {
    lines: Lines<'a>,
    inlays: Iter<'a, (usize, BlockInlay)>,
    line_index: usize,
}

impl<'a> Iterator for BlockElements<'a> {
    type Item = BlockElement<'a>;

    fn next(&mut self) -> Option<Self::Item> {
        if self
            .inlays
            .as_slice()
            .first()
            .map_or(false, |&(line_index, _)| line_index == self.line_index)
        {
            let (_, block_inlay) = self.inlays.next().unwrap();
            return Some(match *block_inlay {
                BlockInlay::Widget(widget) => BlockElement::Widget(widget),
            });
        }
        let line = self.lines.next()?;
        self.line_index += 1;
        Some(BlockElement::Line {
            is_inlay: false,
            line,
        })
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum BlockElement<'a> {
    Line { is_inlay: bool, line: Line<'a> },
    Widget(BlockWidget),
}
