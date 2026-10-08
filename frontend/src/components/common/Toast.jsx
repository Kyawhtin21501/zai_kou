import React from 'react';
import { Check, X } from 'lucide-react';

export default function Toast({ message, actionLabel, onAction, onClose }) {
  return (
    <div className="toast" role="status">
      <span className="toast-check">
        <Check size={16} />
      </span>
      <span>{message}</span>
      {onAction && (
        <button type="button" className="undo-button" onClick={onAction}>
          {actionLabel}
        </button>
      )}
      <button type="button" aria-label="通知を閉じる" onClick={onClose}>
        <X size={16} />
      </button>
    </div>
  );
}
