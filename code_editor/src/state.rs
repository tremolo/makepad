use {
    crate::{
        history::{Edit, EditKind, History},
        layout::{BlockElement, Layout, Line, WrappedElement},
        move_ops,
        selection::{Cursor, Region, Selection},
        settings::Settings,
        str::StrExt,
        text::{Change, Text},
        wrap,
    },
    std::{
        collections::{HashMap, HashSet},
        mem,
    },
};

#[derive(Debug, Default)]
pub struct State {
    sessions: HashMap<SessionId, Session>,
    documents: HashMap<DocumentId, Document>,
    changes: Vec<Change>,
}

impl State {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn layout(&self, session_id: SessionId) -> Layout<'_> {
        let session = &self.sessions[&session_id];
        let document = &self.documents[&session.document];
        Layout {
            y: &session.y,
            column_count: &session.column_count,
            fold_column_index: &session.fold_column_index,
            fold_scale: &session.fold_scale,
            text: document.history.as_text(),
            inline_inlays: &document.inline_inlays,
            block_inlays: &document.block_inlays,
            wrap_byte_indices: &session.wrap_byte_indices,
            wrap_indentation_width: &session.wrap_indentation_width,
        }
    }

    pub fn set_cursor(&mut self, session: SessionId, cursor: Cursor) {
        self.modify_selection(session, |selection, last_added_region, _| {
            selection.set(cursor.into());
            *last_added_region = 0;
        });
    }

    pub fn add_cursor(&mut self, session: SessionId, cursor: Cursor) {
        self.modify_selection(session, |selection, last_added_region, _| {
            *last_added_region = selection.add(cursor.into());
        });
    }

    pub fn update_last_added_cursor(
        &mut self,
        session: SessionId,
        cursor: Cursor,
        reset_anchor: bool,
    ) {
        self.modify_selection(session, |selection, last_added_region, _| {
            *last_added_region = selection.update(*last_added_region, |region| {
                let mut region = region.update_cursor(|_| cursor);
                if reset_anchor {
                    region = region.reset_anchor();
                }
                region
            });
        });
    }

    pub fn move_all_cursors_left(&mut self, session: SessionId, reset_anchor: bool) {
        self.move_all_cursors(session, reset_anchor, |cursor, layout| {
            move_ops::move_left(cursor, layout.as_text().as_lines())
        });
    }

    pub fn move_all_cursors_right(&mut self, session: SessionId, reset_anchor: bool) {
        self.move_all_cursors(session, reset_anchor, |cursor, layout| {
            move_ops::move_right(cursor, layout.as_text().as_lines())
        });
    }

    pub fn insert(&mut self, session: SessionId, text: Text) {
        self.edit(session, EditKind::Insert, |history, region| {
            history.apply_change(Change::Delete(region.start(), region.length()));
            history.apply_change(Change::Insert(region.start(), text.clone()));
        })
    }

    pub fn delete(&mut self, session: SessionId) {
        self.edit(session, EditKind::Delete, |history, region| {
            history.apply_change(Change::Delete(region.start(), region.length()));
        })
    }

    pub fn undo(&mut self, session: SessionId) {
        self.modify_text(session, |history, selection, changes| {
            history.undo(selection, changes)
        });
    }

    pub fn redo(&mut self, session: SessionId) {
        self.modify_text(session, |history, selection, changes| {
            history.redo(selection, changes)
        });
    }

    fn move_all_cursors(
        &mut self,
        session: SessionId,
        reset_anchor: bool,
        mut f: impl FnMut(Cursor, Layout<'_>) -> Cursor,
    ) {
        self.modify_selection(session, |selection, last_added_region, layout| {
            *last_added_region = selection.update_all(*last_added_region, |region| {
                let mut region = region.update_cursor(|cursor| f(cursor, layout));
                if reset_anchor {
                    region = region.reset_anchor();
                }
                region
            });
        })
    }

    fn modify_selection(
        &mut self,
        session_id: SessionId,
        f: impl FnOnce(&mut Selection, &mut usize, Layout<'_>),
    ) {
        let session = self.sessions.get_mut(&session_id).unwrap();
        let document = self.documents.get_mut(&session.document).unwrap();
        f(
            &mut session.selection,
            &mut session.last_added_region,
            Layout {
                y: &session.y,
                column_count: &session.column_count,
                fold_column_index: &session.fold_column_index,
                fold_scale: &session.fold_scale,
                text: document.history.as_text(),
                inline_inlays: &document.inline_inlays,
                block_inlays: &document.block_inlays,
                wrap_byte_indices: &session.wrap_byte_indices,
                wrap_indentation_width: &session.wrap_indentation_width,
            },
        );
        document.history.force_new_undo_group();
    }

    fn edit(
        &mut self,
        session: SessionId,
        kind: EditKind,
        mut f: impl FnMut(&mut Edit<'_, '_>, Region),
    ) {
        self.modify_text(session, |history, selection, changes| {
            let mut edit = history.edit(session, kind, selection, changes);
            for &region in selection {
                f(&mut edit, region)
            }
            None
        })
    }

    fn modify_text(
        &mut self,
        session_id: SessionId,
        f: impl FnOnce(&mut History, &Selection, &mut Vec<Change>) -> Option<Selection>,
    ) {
        let session = self.sessions.get_mut(&session_id).unwrap();
        let document = self.documents.get_mut(&session.document).unwrap();
        let mut changes = mem::take(&mut self.changes);
        let selection = f(&mut document.history, &session.selection, &mut changes);
        document.update_after_text_modified(&changes);
        session.update_after_text_modified(document, &changes, selection);
        for &other_session_id in &document.sessions {
            if other_session_id == session_id {
                continue;
            }
            self.sessions
                .get_mut(&other_session_id)
                .unwrap()
                .update_after_text_modified(&document, &changes, None);
        }
        changes.clear();
        self.changes = changes;
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct SessionId(usize);

#[derive(Clone, Debug, Eq, Hash, PartialEq)]
pub enum InlineInlay {
    Text(String),
    Widget(InlineWidget),
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct InlineWidget {
    pub id: usize,
    pub column_count: usize,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum BlockInlay {
    Widget(BlockWidget),
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct BlockWidget {
    pub id: usize,
    pub height: f64,
}

#[derive(Debug)]
struct Session {
    settings: Settings,
    y: Vec<f64>,
    column_count: Vec<usize>,
    fold_column_index: Vec<usize>,
    fold_scale: Vec<f64>,
    wrap_byte_indices: Vec<Vec<usize>>,
    wrap_indentation_width: Vec<usize>,
    selection: Selection,
    last_added_region: usize,
    document: DocumentId,
}

impl Session {
    fn update_y(&mut self, document: &Document) {
        let line_start = self.y.len();
        let line_end = document.history.as_text().as_lines().len();
        if line_start == line_end + 1 {
            return;
        }
        let layout = Layout {
            y: &[],
            column_count: &self.column_count,
            fold_column_index: &self.fold_column_index,
            fold_scale: &self.fold_scale,
            text: document.history.as_text(),
            inline_inlays: &document.inline_inlays,
            block_inlays: &document.block_inlays,
            wrap_byte_indices: &self.wrap_byte_indices,
            wrap_indentation_width: &self.wrap_indentation_width,
        };
        let mut y = if line_start == 0 {
            0.0
        } else {
            self.y[line_start - 1] + layout.line(line_start - 1).height()
        };
        for element in layout.block_elements(line_start, line_end) {
            match element {
                BlockElement::Line { is_inlay, line } => {
                    if !is_inlay {
                        self.y.push(y);
                    }
                    y += line.height();
                }
                BlockElement::Widget(widget) => {
                    y += widget.height;
                }
            }
        }
        self.y.push(y);
    }

    fn update_column_count(&mut self, document: &Document, line_index: usize) {
        let mut max_column_count = 0;
        let mut column_count = 0;
        let line = Line {
            y: self.y[line_index],
            column_count: self.column_count[line_index],
            fold_column_index: self.fold_column_index[line_index],
            fold_scale: self.fold_scale[line_index],
            text: &document.history.as_text().as_lines()[line_index],
            inlays: &document.inline_inlays[line_index],
            wrap_byte_indices: &self.wrap_byte_indices[line_index],
            wrap_indentation_width: self.wrap_indentation_width[line_index],
        };
        for element in line.wrapped_elements() {
            match element {
                WrappedElement::Text { text, .. } => {
                    column_count += text.column_count(self.settings.tab_column_count);
                }
                WrappedElement::Widget(widget) => {
                    column_count += widget.column_count;
                }
                WrappedElement::Wrap => {
                    max_column_count = max_column_count.max(column_count);
                    column_count = line.wrap_indentation_width;
                }
            }
        }
        self.column_count[line_index] = max_column_count.max(column_count);
    }

    fn update_wrap_data(&mut self, document: &Document, line_index: usize) {
        self.wrap_indentation_width[line_index] = wrap::wrap(
            Line {
                y: self.y[line_index],
                column_count: self.column_count[line_index],
                fold_column_index: self.fold_column_index[line_index],
                fold_scale: self.fold_scale[line_index],
                text: &document.history.as_text().as_lines()[line_index],
                inlays: &document.inline_inlays[line_index],
                wrap_byte_indices: &[],
                wrap_indentation_width: 0,
            },
            80,
            self.settings.tab_column_count,
            &mut self.wrap_byte_indices[line_index],
        );
        self.y.truncate(line_index + 1);
        self.update_column_count(document, line_index);
    }

    fn update_after_text_modified(
        &mut self,
        document: &Document,
        changes: &[Change],
        selection: Option<Selection>,
    ) {
        if let Some(selection) = selection {
            self.selection = selection;
        } else {
            for change in changes {
                self.selection.apply_change(&change);
            }
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
struct DocumentId(usize);

#[derive(Debug)]
struct Document {
    sessions: HashSet<SessionId>,
    history: History,
    inline_inlays: Vec<Vec<(usize, InlineInlay)>>,
    block_inlays: Vec<(usize, BlockInlay)>,
}

impl Document {
    fn update_after_text_modified(&mut self, _changes: &[Change]) {
        // TODO
    }
}
