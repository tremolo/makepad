use std::{cmp::Ordering, ops::{Add, AddAssign, Sub, SubAssign}};

#[derive(Clone, Debug, Eq, Hash, PartialEq)]
pub struct Text {
    lines: Vec<String>,
}

impl Text {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn is_empty(&self) -> bool {
        self.length() == Length::empty()
    }

    pub fn length(&self) -> Length {
        Length {
            line_count: self.lines.len() - 1,
            byte_count: self.lines.last().unwrap().len(),
        }
    }

    pub fn as_lines(&self) -> &[String] {
        &self.lines
    }

    pub fn slice(&self, start: Position, length: Length) -> Text {
        let end = start + length;
        Self {
            lines: if length.line_count == 0 {
                [self.lines[start.line_index]
                    [start.byte_index..start.byte_index + length.byte_count]
                    .to_string()]
                .into()
            } else {
                let front = &self.lines[start.line_index][start.byte_index..];
                let middle = self.lines[start.line_index + 1..end.line_index]
                    .iter()
                    .map(|string| string.as_str());
                let back = &self.lines[end.line_index][..end.byte_index];
                [front]
                    .into_iter()
                    .chain(middle)
                    .chain([back])
                    .map(|string| string.to_owned())
                    .collect()
            },
        }
    }

    pub fn apply_change(&mut self, change: Change) {
        match change {
            Change::Insert(position, text) => self.insert(position, text),
            Change::Delete(start, length) => self.delete(start, length),
        }
    }

    pub fn into_lines(self) -> Vec<String> {
        self.lines
    }

    fn insert(&mut self, position: Position, text: Text) {
        if text.length().line_count == 0 {
            self.lines[position.line_index].replace_range(
                position.byte_index..position.byte_index,
                text.lines.first().unwrap(),
            );
        } else {
            let before = &self.lines[position.line_index][..position.byte_index];
            let after = &self.lines[position.line_index][position.byte_index..];
            let mut lines = text.lines;
            lines.first_mut().unwrap().replace_range(..0, before);
            lines.last_mut().unwrap().push_str(after);
            self.lines
                .splice(position.line_index..position.line_index + 1, lines);
        }
    }

    fn delete(&mut self, start: Position, length: Length) {
        let end = start + length;
        if length.line_count == 0 {
            self.lines[start.line_index].replace_range(start.byte_index..end.byte_index, "");
        } else {
            let before = &self.lines[start.line_index][..start.byte_index];
            let after = &self.lines[end.line_index][end.byte_index..];
            self.lines.splice(
                start.line_index..end.line_index + 1,
                [[before, after].into_iter().collect()],
            );
        }
    }
}

impl Default for Text {
    fn default() -> Self {
        Self {
            lines: vec![String::new()],
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct Position {
    pub line_index: usize,
    pub byte_index: usize,
}

impl Position {
    pub fn apply_change(self, change: &Change, drift: Drift) -> Self {
        match *change {
            Change::Insert(position, ref text) => match self.cmp(&position) {
                Ordering::Less => self,
                Ordering::Equal => match drift {
                    Drift::Before => self + text.length(),
                    Drift::After => self,
                },
                Ordering::Greater => position + text.length() + (self - position),
            },
            Change::Delete(start, length) => {
                if self < start {
                    self
                } else {
                    start + (self - (start + length).min(self))
                }
            }
        }
    }
}

impl Add<Length> for Position {
    type Output = Self;

    fn add(self, length: Length) -> Self::Output {
        if length.line_count == 0 {
            Self {
                line_index: self.line_index,
                byte_index: self.byte_index + length.byte_count,
            }
        } else {
            Self {
                line_index: self.line_index + length.line_count,
                byte_index: length.byte_count,
            }
        }
    }
}

impl AddAssign<Length> for Position {
    fn add_assign(&mut self, length: Length) {
        *self = *self + length;
    }
}

impl Sub for Position {
    type Output = Length;

    fn sub(self, other: Self) -> Self::Output {
        if self.line_index == other.line_index {
            Length {
                line_count: 0,
                byte_count: self.byte_index - other.byte_index,
            }
        } else {
            Length {
                line_count: self.line_index - other.line_index,
                byte_count: self.byte_index,
            }
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, Hash, PartialEq)]
pub struct Length {
    pub line_count: usize,
    pub byte_count: usize,
}

impl Length {
    pub fn empty() -> Self {
        Self::default()
    }
}

impl Add for Length {
    type Output = Self;

    fn add(self, other: Self) -> Self::Output {
        if other.line_count == 0 {
            Self {
                line_count: self.line_count,
                byte_count: self.byte_count + other.byte_count,
            }
        } else {
            Self {
                line_count: self.line_count + other.line_count,
                byte_count: other.byte_count,
            }
        }
    }
}

impl AddAssign for Length {
    fn add_assign(&mut self, other: Self) {
        *self = *self + other;
    }
}

impl Sub for Length {
    type Output = Self;

    fn sub(self, other: Self) -> Self::Output {
        if self.line_count == other.line_count {
            Self {
                line_count: 0,
                byte_count: self.byte_count - other.byte_count,
            }
        } else {
            Self {
                line_count: self.line_count - other.line_count,
                byte_count: self.byte_count,
            }
        }
    }
}

impl SubAssign for Length {
    fn sub_assign(&mut self, other: Self) {
        *self = *self - other;
    }
}

#[derive(Clone, Debug, Eq, Hash, PartialEq)]
pub enum Change {
    Insert(Position, Text),
    Delete(Position, Length),
}

impl Change {
    pub fn invert(&self, text: &Text) -> Self {
        match *self {
            Self::Insert(position, ref text) => Self::Delete(position, text.length()),
            Self::Delete(start, length) => Self::Insert(start, text.slice(start, length)),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum Drift {
    Before,
    After,
}
