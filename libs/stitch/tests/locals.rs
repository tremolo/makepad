use makepad_stitch::{Engine, Linker, Module, Store, Val};

/// A local read onto the stack three times, then written while the reads
/// are still pending: the compiler must keep all three old values.
const WAT: &str = r#"
(module
  (func (export "f") (param i32) (result i32)
    (local.get 0)
    (local.get 0)
    (local.get 0)
    (local.set 0 (i32.const 100))
    (i32.add)
    (i32.add)
    (local.get 0)
    (i32.add)))
"#;

#[test]
fn a_local_set_under_pending_reads_keeps_them() {
    let buffer = wast::parser::ParseBuffer::new(WAT).unwrap();
    let mut wat = wast::parser::parse::<wast::Wat>(&buffer).unwrap();
    let binary = wat.encode().unwrap();
    let mut store = Store::new(Engine::new());
    let module = Module::new(store.engine(), &binary).unwrap();
    let instance = Linker::new().instantiate(&mut store, &module).unwrap();
    let f = instance.exported_func("f").unwrap();
    let mut out = [Val::I32(0)];
    f.call(&mut store, &[Val::I32(5)], &mut out).unwrap();
    assert!(matches!(out[0], Val::I32(115)), "{:?}", out[0]);
}
