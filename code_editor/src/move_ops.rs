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
    position.line == 0
}

fn is_at_last_line(position: Position, lines: &[String]) -> bool {
    position.line == lines.len()
}

fn is_at_start_of_line(position: Position) -> bool {
    position.byte == 0
}

fn is_at_end_of_line(position: Position, lines: &[String]) -> bool {
    position.byte == lines[position.line].len()
}

fn move_to_prev_grapheme(position: Position, lines: &[String]) -> Position {
    Position {
        line: position.line,
        byte: lines[position.line][..position.byte]
            .grapheme_indices()
            .next_back()
            .map(|(index, _)| index)
            .unwrap(),
    }
}

fn move_to_next_grapheme(position: Position, lines: &[String]) -> Position {
    let line = &lines[position.line];
    Position {
        line: position.line,
        byte: line[position.byte..]
            .grapheme_indices()
            .nth(1)
            .map(|(index, _)| position.byte + index)
            .unwrap_or(line.len()),
    }
}

fn move_to_end_of_prev_line(position: Position, lines: &[String]) -> Position {
    let prev_line = position.line - 1;
    Position {
        line: prev_line,
        byte: lines[prev_line].len(),
    }
}

fn move_to_start_of_next_line(position: Position) -> Position {
    Position {
        line: position.line + 1,
        byte: 0,
    }
}
