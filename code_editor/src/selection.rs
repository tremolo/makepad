use {
    crate::text::{Change, Drift, Length, Position},
    std::{ops::Deref, slice::Iter},
};

#[derive(Clone, Debug, Eq, Hash, PartialEq)]
pub struct Selection {
    regions: Vec<Region>,
}

impl Selection {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn as_regions(&self) -> &[Region] {
        &self.regions
    }

    pub fn update(&mut self, index: usize, f: impl FnOnce(Region) -> Region) -> usize {
        let region = self.regions[index];
        self.regions[index] = f(region);
        let mut index = index;
        while index > 0 {
            let prev_index = index - 1;
            if !self.regions[prev_index].overlaps_with(self.regions[index]) {
                break;
            }
            self.regions.remove(prev_index);
            index -= 1;
        }
        while index + 1 < self.regions.len() {
            let next_index = index + 1;
            if !self.regions[index].overlaps_with(self.regions[next_index]) {
                break;
            }
            self.regions.remove(next_index);
        }
        index
    }

    pub fn update_all(&mut self, index: usize, mut f: impl FnMut(Region) -> Region) -> usize {
        for region in &mut self.regions {
            *region = f(*region);
        }
        let mut index = index;
        let mut current_index = 0;
        while current_index + 1 < self.regions.len() {
            let next_index = current_index + 1;
            let current_region = self.regions[current_index];
            let next_region = self.regions[next_index];
            assert!(current_region.start() <= next_region.start());
            if let Some(merged_selection) = current_region.merge_with(next_region) {
                self.regions[current_index] = merged_selection;
                self.regions.remove(next_index);
                if next_index < index {
                    index -= 1;
                }
            } else {
                current_index += 1;
            }
        }
        index
    }

    pub fn apply_change(&mut self, change: &Change) {
        for region in &mut self.regions {
            *region = region.apply_change(change);
        }
    }

    pub fn add(&mut self, region: Region) -> usize {
        let index = match self
            .regions
            .binary_search_by_key(&region.start(), |region| region.start())
        {
            Ok(index) => index,
            Err(index) => index,
        };
        self.regions.insert(index, region);
        index
    }

    pub fn set(&mut self, region: Region) {
        self.regions.clear();
        self.regions.push(region);
    }
}

impl Default for Selection {
    fn default() -> Self {
        Self {
            regions: vec![Region::default()],
        }
    }
}

impl Deref for Selection {
    type Target = [Region];

    fn deref(&self) -> &Self::Target {
        self.as_regions()
    }
}

impl<'a> IntoIterator for &'a Selection {
    type Item = &'a Region;
    type IntoIter = Iter<'a, Region>;

    fn into_iter(self) -> Self::IntoIter {
        self.iter()
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, Hash, PartialEq)]
pub struct Region {
    pub cursor: Cursor,
    pub anchor: Position,
}

impl Region {
    pub fn is_empty(self) -> bool {
        self.length() == Length::empty()
    }

    pub fn overlaps_with(self, other: Self) -> bool {
        if self.is_empty() || other.is_empty() {
            self.end() >= other.start()
        } else {
            self.end() > other.start()
        }
    }

    pub fn length(self) -> Length {
        self.end() - self.start()
    }

    pub fn start(self) -> Position {
        self.cursor.position.min(self.anchor)
    }

    pub fn end(self) -> Position {
        self.cursor.position.max(self.anchor)
    }

    pub fn update_cursor(self, f: impl FnOnce(Cursor) -> Cursor) -> Self {
        Self {
            cursor: f(self.cursor),
            ..self
        }
    }

    pub fn reset_anchor(self) -> Self {
        Self {
            anchor: self.cursor.position,
            ..self
        }
    }

    pub fn apply_change(self, change: &Change) -> Self {
        if self.cursor.position <= self.anchor {
            Self {
                cursor: Cursor {
                    position: self.cursor.position.apply_change(change, Drift::Before),
                    ..self.cursor
                },
                anchor: self.anchor.apply_change(change, Drift::After),
            }
        } else {
            Self {
                cursor: Cursor {
                    position: self.cursor.position.apply_change(change, Drift::After),
                    ..self.cursor
                },
                anchor: self.anchor.apply_change(change, Drift::Before),
            }
        }
    }

    pub fn merge_with(self, other: Self) -> Option<Self> {
        if !self.overlaps_with(other) {
            return None;
        }
        Some(if self.cursor.position <= self.anchor {
            Self {
                cursor: self.cursor,
                anchor: other.anchor,
            }
        } else {
            Self {
                cursor: other.cursor,
                anchor: self.anchor,
            }
        })
    }
}

impl From<Cursor> for Region {
    fn from(cursor: Cursor) -> Self {
        Self {
            cursor,
            anchor: cursor.position,
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct Cursor {
    pub position: Position,
    pub affinity: Affinity,
}

impl Cursor {
    pub fn update_position(self, f: impl FnOnce(Position) -> Position) -> Self {
        Self {
            position: f(self.position),
            ..self
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub enum Affinity {
    Before,
    After,
}

impl Default for Affinity {
    fn default() -> Self {
        Self::Before
    }
}
