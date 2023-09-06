use crate::{
    selection::Selection,
    state::SessionId,
    text::{Change, Text},
};

#[derive(Clone, Debug, Default, Eq, Hash, PartialEq)]
pub struct History {
    text: Text,
    prev_edit: Option<(SessionId, EditKind)>,
    undo_stack: EditStack,
    redo_stack: EditStack,
}

impl History {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn as_text(&self) -> &Text {
        &self.text
    }

    pub fn force_new_undo_group(&mut self) {
        self.prev_edit = None;
    }

    pub fn edit<'a, 'b>(
        &'a mut self,
        session: SessionId,
        kind: EditKind,
        selection: &Selection,
        changes: &'b mut Vec<Change>,
    ) -> Edit<'a, 'b> {
        if !self.prev_edit.map_or(false, |(prev_session, prev_kind)| {
            prev_session == session && prev_kind.groups_with(kind)
        }) {
            self.prev_edit = Some((session, kind));
            self.undo_stack.push_selection(selection.clone());
        }
        self.redo_stack.clear();
        Edit {
            history: self,
            changes,
        }
    }

    pub fn undo(&mut self, selection: &Selection, changes: &mut Vec<Change>) -> Option<Selection> {
        let new_selection = self.undo_stack.pop_until_selection(changes);
        if new_selection.is_some() {
            self.redo_stack.push_selection(selection.clone());
            for change in changes {
                let inverted_change = change.invert(&self.text);
                self.text.apply_change(change.clone());
                self.redo_stack.push_change(inverted_change);
            }
        }
        new_selection
    }

    pub fn redo(&mut self, selection: &Selection, changes: &mut Vec<Change>) -> Option<Selection> {
        let new_selection = self.redo_stack.pop_until_selection(changes);
        if new_selection.is_some() {
            self.undo_stack.push_selection(selection.clone());
            for change in changes {
                let inverted_change = change.invert(&self.text);
                self.text.apply_change(change.clone());
                self.undo_stack.push_change(inverted_change);
            }
        }
        new_selection
    }
}

impl From<Text> for History {
    fn from(text: Text) -> Self {
        Self {
            text,
            ..Self::default()
        }
    }
}

#[derive(Debug)]
pub struct Edit<'a, 'b> {
    history: &'a mut History,
    changes: &'b mut Vec<Change>,
}

impl<'a, 'b> Edit<'a, 'b> {
    pub fn apply_change(&mut self, change: Change) {
        let inverted_change = change.invert(&self.history.text);
        self.history.text.apply_change(change.clone());
        self.history.undo_stack.push_change(inverted_change);
        self.changes.push(change);
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum EditKind {
    Insert,
    Delete,
}

impl EditKind {
    fn groups_with(self, other: Self) -> bool {
        self == other
    }
}

#[derive(Clone, Debug, Default, Eq, Hash, PartialEq)]
struct EditStack {
    entries: Vec<EditStackEntry>,
    changes: Vec<Change>,
}

impl EditStack {
    fn push_selection(&mut self, selection: Selection) {
        self.entries.push(EditStackEntry {
            selection,
            changes_start: self.changes.len(),
        })
    }

    fn push_change(&mut self, change: Change) {
        assert!(!self.entries.is_empty());
        self.changes.push(change);
    }

    fn pop_until_selection(&mut self, changes: &mut Vec<Change>) -> Option<Selection> {
        match self.entries.pop() {
            Some(group) => {
                changes.extend(self.changes.drain(group.changes_start..).rev());
                Some(group.selection)
            }
            None => None,
        }
    }

    fn clear(&mut self) {
        self.entries.clear();
        self.changes.clear();
    }
}

#[derive(Clone, Debug, Default, Eq, Hash, PartialEq)]
struct EditStackEntry {
    selection: Selection,
    changes_start: usize,
}
