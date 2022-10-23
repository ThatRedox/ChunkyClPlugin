pub struct ResourcePalette {
    pub resources: Vec<Vec<u32>>,
}

impl ResourcePalette {
    pub fn new() -> Self {
        ResourcePalette { resources: vec![] }
    }

    pub fn put(&mut self, resource: Vec<u32>) -> Result<i32, String> {
        let index = self.resources.len();
        self.resources.push(resource);

        index.try_into().map_err(|_| "Too many resources.".to_string())
    }
}
