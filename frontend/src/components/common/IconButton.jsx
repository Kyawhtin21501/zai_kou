import React from 'react';

export default function IconButton({
  label,
  className = '',
  children,
  ...props
}) {
  return (
    <button
      type="button"
      className={`icon-button ${className}`.trim()}
      aria-label={label}
      {...props}
    >
      {children}
    </button>
  );
}
