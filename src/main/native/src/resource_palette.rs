pub struct ResourcePalette {
    pub resources: Vec<Vec<u32>>,
}

impl ResourcePalette {
    pub fn new() -> Self {
        ResourcePalette { resources: vec![] }
    }

    pub fn put(&mut self, resource: Vec<u32>) -> usize {
        let index = self.resources.len();
        self.resources.push(resource);
        index
    }
}
