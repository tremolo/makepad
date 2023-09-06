use crate::{selection::Cursor, str::StrExt, text::Position};

pub fn move_left(cursor: Cursor, lines: &[String]) -> Cursor {
    cursor.update_position(|position| {
        if !is_at_start_of_line(position) {
            return move_to_prev_grapheme(position, lines);
        }
        if !is_at_first_line(cursor.position) {
            return move_to_end_of_prev_line(position, lines);
        }
        position
    })
}

pub fn move_right(cursor: Cursor, lines: &[String]) -> Cursor {
    cursor.update_position(|position| {
        if !is_at_end_of_line(cursor.position, lines) {
            return move_to_next_grapheme(position, lines);
        }
        if !is_at_last_line(cursor.position, lines) {
            return move_to_start_of_next_line(position);
        }
        position
    })
}

fn is_at_first_line(position: Position) -> bool {
    position.line_index == 0
}

fn is_at_last_line(position: Position, lines: &[String]) -> bool {
    position.line_index == lines.len()
}

fn is_at_start_of_line(position: Position) -> bool {
    position.byte_index == 0
}

fn is_at_end_of_line(position: Position, lines: &[String]) -> bool {
    position.byte_index == lines[position.line_index].len()
}

fn move_to_prev_grapheme(position: Position, lines: &[String]) -> Position {
    Position {
        line_index: position.line_index,
        byte_index: lines[position.line_index][..position.byte_index]
            .grapheme_indices()
            .next_back()
            .map(|(index, _)| index)
            .unwrap(),
    }
}

fn move_to_next_grapheme(position: Position, lines: &[String]) -> Position {
    let line = &lines[position.line_index];
    Position {
        line_index: position.line_index,
        byte_index: line[position.byte_index..]
            .grapheme_indices()
            .nth(1)
            .map(|(index, _)| position.byte_index + index)
            .unwrap_or(line.len()),
    }
}

fn move_to_end_of_prev_line(position: Position, lines: &[String]) -> Position {
    let prev_line = position.line_index - 1;
    Position {
        line_index: prev_line,
        byte_index: lines[prev_line].len(),
    }
}

fn move_to_start_of_next_line(position: Position) -> Position {
    Position {
        line_index: position.line_index + 1,
        byte_index: 0,
    }
}
