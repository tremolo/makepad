use std::ops::{Add, AddAssign, Sub, SubAssign};

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
            lines: self.lines.len() - 1,
            bytes: self.lines.last().unwrap().len(),
        }
    }

    pub fn as_lines(&self) -> &[String] {
        &self.lines
    }

    pub fn slice(&self, start: Position, length: Length) -> Text {
        let end = start + length;
        Self {
            lines: if length.lines == 0 {
                [self.lines[start.line][start.byte..start.byte + length.bytes].to_string()].into()
            } else {
                let front = &self.lines[start.line][start.byte..];
                let middle = self.lines[start.line + 1..end.line]
                    .iter()
                    .map(|string| string.as_str());
                let back = &self.lines[end.line][..end.byte];
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
        if text.length().lines == 0 {
            self.lines[position.line]
                .replace_range(position.byte..position.byte, text.lines.first().unwrap());
        } else {
            let before = &self.lines[position.line][..position.byte];
            let after = &self.lines[position.line][position.byte..];
            let mut lines = text.lines;
            lines.first_mut().unwrap().replace_range(..0, before);
            lines.last_mut().unwrap().push_str(after);
            self.lines.splice(position.line..position.line + 1, lines);
        }
    }

    fn delete(&mut self, start: Position, length: Length) {
        let end = start + length;
        if length.lines == 0 {
            self.lines[start.line].replace_range(start.byte..end.byte, "");
        } else {
            let before = &self.lines[start.line][..start.byte];
            let after = &self.lines[end.line][end.byte..];
            self.lines.splice(
                start.line..end.line + 1,
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
    pub line: usize,
    pub byte: usize,
}

impl Position {
    pub fn apply_change(self, _change: &Change, _drift: Drift) -> Self {
        unimplemented!()
    }
}

impl Add<Length> for Position {
    type Output = Self;

    fn add(self, length: Length) -> Self::Output {
        if length.lines == 0 {
            Self {
                line: self.line,
                byte: self.byte + length.bytes,
            }
        } else {
            Self {
                line: self.line + length.lines,
                byte: length.bytes,
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
        if self.line == other.line {
            Length {
                lines: 0,
                bytes: self.byte - other.byte,
            }
        } else {
            Length {
                lines: self.line - other.line,
                bytes: self.byte,
            }
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, Hash, PartialEq)]
pub struct Length {
    pub lines: usize,
    pub bytes: usize,
}

impl Length {
    pub fn empty() -> Self {
        Self::default()
    }
}

impl Add for Length {
    type Output = Self;

    fn add(self, other: Self) -> Self::Output {
        if other.lines == 0 {
            Self {
                lines: self.lines,
                bytes: self.bytes + other.bytes,
            }
        } else {
            Self {
                lines: self.lines + other.lines,
                bytes: other.bytes,
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
        if self.lines == other.lines {
            Self {
                lines: 0,
                bytes: self.bytes - other.bytes,
            }
        } else {
            Self {
                lines: self.lines - other.lines,
                bytes: self.bytes,
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
