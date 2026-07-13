import { isValidElement } from 'react';
import type { ReactNode } from 'react';

function isEmptyReactNode(node: ReactNode): boolean {
  if (node === null || node === undefined || node === '' || typeof node === 'boolean') return true;
  if (Array.isArray(node)) return node.length === 0 || node.every(isEmptyReactNode);
  if (isValidElement(node)) {
    // Custom components may render content even without children prop,
    // so only DOM elements with empty children are considered empty.
    if (typeof node.type !== 'string') return false;
    const children = (node.props as { children?: ReactNode }).children;
    return isEmptyReactNode(children);
  }
  return false;
}

export function formatEmpty(value: unknown): ReactNode {
  if (value === null || value === undefined || value === '') return '—';
  if (isEmptyReactNode(value as ReactNode)) return '—';
  return value as ReactNode;
}
