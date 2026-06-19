const { EventEmitter } = require('events');

class TrafficStore extends EventEmitter {
  constructor() {
    super();
    this.entries = [];
    this.nextId = 1;
  }

  add(data) {
    const entry = { id: this.nextId++, time: Date.now(), ...data };
    this.entries.push(entry);
    this.emit('entry', entry);
    return entry;
  }

  update(id, patch) {
    const entry = this.entries.find(e => e.id === id);
    if (!entry) return;
    Object.assign(entry, patch);
    this.emit('update', entry);
  }

  clear() {
    this.entries = [];
    this.nextId = 1;
    this.emit('clear');
  }
}

module.exports = new TrafficStore();
