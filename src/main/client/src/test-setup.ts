import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  cleanup();
});

vi.mock('xterm', () => {
  class MockTerminal {
    static instances: MockTerminal[] = [];
    cols = 120;
    rows = 36;
    private onDataHandler: ((data: string) => void) | null = null;
    focus = vi.fn();
    write = vi.fn();
    writeln = vi.fn();
    dispose = vi.fn();

    constructor() {
      MockTerminal.instances.push(this);
    }

    loadAddon() {}
    open() {}
    onData(handler: (data: string) => void) {
      this.onDataHandler = handler;
      return { dispose() {} };
    }
    emitData(data: string) {
      this.onDataHandler?.(data);
    }
    static reset() {
      MockTerminal.instances = [];
    }
  }

  return { Terminal: MockTerminal };
});

vi.mock('xterm-addon-fit', () => {
  class MockFitAddon {
    fit() {}
  }

  return { FitAddon: MockFitAddon };
});
