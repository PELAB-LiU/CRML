# Source: https://github.com/PELAB-LiU/Text2VQL/blob/extension/dataset_construction/text2vql/seed/util.py
class AttrDict:
    def __init__(self, data=None):
        object.__setattr__(self, "_data", {})
        if data:
            object.__setattr__(self, "_data", data)

    def wrap(self, value):
        if isinstance(value, AttrDict):
            return value
        if isinstance(value, dict):
            return AttrDict(value)
        if isinstance(value, list):
            return [self.wrap(x) for x in value]
        return value
    
    def __getattr__(self, name):
        # Called when attribute is not found normally
        if name in self._data:
            value = self._data[name]
            return self.wrap(value)
        raise AttributeError(f"No attribute named '{name}'")
    
    def __getitem__(self, key):
        return self.wrap(self._data[key])
    
    def keys(self):
        return self._data.keys()