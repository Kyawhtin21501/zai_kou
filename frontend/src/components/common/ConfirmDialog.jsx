import React from 'react';
import Modal from './Modal';
import Button from './Button';

export default function ConfirmDialog({
  title,
  subtitle,
  eyebrow,
  children,
  confirmLabel = '確認',
  cancelLabel = 'キャンセル',
  confirmIcon: Icon,
  variant = 'danger',
  pending = false,
  onConfirm,
  onClose,
}) {
  return (
    <Modal
      title={title}
      subtitle={subtitle}
      eyebrow={eyebrow}
      onClose={onClose}
    >
      {children}
      <div className="flex justify-end gap-3">
        <Button
          autoFocus
          variant="secondary"
          disabled={pending}
          onClick={onClose}
        >
          {cancelLabel}
        </Button>
        <Button variant={variant} disabled={pending} onClick={onConfirm}>
          {Icon && <Icon size={16} />}
          {pending ? '処理中…' : confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}
