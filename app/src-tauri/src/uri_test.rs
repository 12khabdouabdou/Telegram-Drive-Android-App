pub fn init(builder: tauri::Builder<tauri::Wry>) {
    builder.register_uri_scheme_protocol("tgstream", |app_handle, req| {
        tauri::http::Response::builder().status(200).body(Vec::new()).unwrap()
    });
}
