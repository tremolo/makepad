use {
    crate::{
        state::{BlockInlay, BlockWidget, InlineInlay, InlineWidget},
        text::Text,
    },
    std::slice::Iter,
};

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Layout<'a> {
    pub fold_position: &'a [usize],
    pub fold_scale: &'a [f64],
    pub text: &'a Text,
    pub inline_inlays: &'a [Vec<(usize, InlineInlay)>],
    pub wrap_positions: &'a [Vec<usize>],
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

    pub fn line(&self, index: usize) -> Line<'_> {
        Line {
            fold_position: self.fold_position[index],
            fold_scale: self.fold_scale[index],
            text: &self.text.as_lines()[index],
            inlays: &self.inline_inlays[index],
            wrap_positions: &self.wrap_positions[index],
            wrap_indentation_width: self.wrap_indentation_width[index],
        }
    }

    pub fn lines(&self, start: usize, end: usize) -> Lines<'_> {
        Lines {
            fold_position: self.fold_position[start..end].iter(),
            fold_scale: self.fold_scale[start..end].iter(),
            text: self.text.as_lines()[start..end].iter(),
            inlays: self.inline_inlays[start..end].iter(),
            wrap_positions: self.wrap_positions[start..end].iter(),
            wrap_indentation_width: self.wrap_indentation_width[start..end].iter(),
        }
    }

    pub fn block_elements(&self, start: usize, end: usize) -> BlockElements<'_> {
        let mut inlays = self.block_inlays.iter();
        while inlays
            .as_slice()
            .first()
            .map_or(false, |&(index, _)| index < start)
        {
            inlays.next();
        }
        BlockElements {
            lines: self.lines(start, end),
            inlays,
            position: start,
        }
    }
}

#[derive(Clone, Debug)]
pub struct Lines<'a> {
    pub fold_position: Iter<'a, usize>,
    pub fold_scale: Iter<'a, f64>,
    pub text: Iter<'a, String>,
    pub inlays: Iter<'a, Vec<(usize, InlineInlay)>>,
    pub wrap_positions: Iter<'a, Vec<usize>>,
    pub wrap_indentation_width: Iter<'a, usize>,
}

impl<'a> Iterator for Lines<'a> {
    type Item = Line<'a>;

    fn next(&mut self) -> Option<Self::Item> {
        Some(Line {
            fold_position: *self.fold_position.next()?,
            fold_scale: *self.fold_scale.next()?,
            text: self.text.next()?,
            inlays: self.inlays.next()?,
            wrap_positions: self.wrap_positions.next()?,
            wrap_indentation_width: *self.wrap_indentation_width.next()?,
        })
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Line<'a> {
    pub fold_position: usize,
    pub fold_scale: f64,
    pub text: &'a str,
    pub inlays: &'a [(usize, InlineInlay)],
    pub wrap_positions: &'a [usize],
    pub wrap_indentation_width: usize,
}

impl<'a> Line<'a> {
    pub fn height(&self) -> usize {
        self.wrap_positions.len() + 1
    }

    pub fn folded_height(&self) -> f64 {
        self.height() as f64 * self.fold_scale
    }

    pub fn inline_elements(&self) -> InlineElements<'_> {
        InlineElements {
            text: self.text,
            inlays: self.inlays.iter(),
            position: 0,
        }
    }

    pub fn wrapped_elements(&self) -> WrappedElements<'_> {
        let mut elements = self.inline_elements();
        WrappedElements {
            element: elements.next(),
            elements,
            wrap_positions: self.wrap_positions.iter(),
            position: 0,
        }
    }
}

#[derive(Clone, Debug)]
pub struct InlineElements<'a> {
    text: &'a str,
    inlays: Iter<'a, (usize, InlineInlay)>,
    position: usize,
}

impl<'a> Iterator for InlineElements<'a> {
    type Item = InlineElement<'a>;

    fn next(&mut self) -> Option<Self::Item> {
        if self
            .inlays
            .as_slice()
            .first()
            .map_or(false, |&(index, _)| index == self.position)
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
        if let Some(&(position, _)) = self.inlays.as_slice().first() {
            len = len.min(position - self.position);
        }
        let (text, remaining_text) = self.text.split_at(len);
        self.text = remaining_text;
        self.position += text.len();
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
    wrap_positions: Iter<'a, usize>,
    position: usize,
}

impl<'a> Iterator for WrappedElements<'a> {
    type Item = WrappedElement<'a>;

    fn next(&mut self) -> Option<Self::Item> {
        if self
            .wrap_positions
            .as_slice()
            .first()
            .map_or(false, |&index| index == self.position)
        {
            self.wrap_positions.next();
            return Some(WrappedElement::Wrap);
        }
        Some(match self.element.take()? {
            InlineElement::Text { is_inlay, text } => {
                let mut len = text.len();
                if let Some(&index) = self.wrap_positions.as_slice().first() {
                    len = len.min(index - self.position);
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
                self.position += text.len();
                WrappedElement::Text { is_inlay, text }
            }
            InlineElement::Widget(widget) => {
                self.position += 1;
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
    position: usize,
}

impl<'a> Iterator for BlockElements<'a> {
    type Item = BlockElement<'a>;

    fn next(&mut self) -> Option<Self::Item> {
        if self
            .inlays
            .as_slice()
            .first()
            .map_or(false, |&(index, _)| index == self.position)
        {
            let (_, block_inlay) = self.inlays.next().unwrap();
            return Some(match *block_inlay {
                BlockInlay::Widget(widget) => BlockElement::Widget(widget),
            });
        }
        let line = self.lines.next()?;
        self.position += 1;
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
