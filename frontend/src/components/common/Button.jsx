import React from 'react';

const variants = {
  primary: 'primary-button',
  secondary: 'secondary-button',
  danger: 'danger-button',
};

export default function Button({
  variant = 'primary',
  type = 'button',
  className = '',
  children,
  ...props
}) {
  return (
    <button
      type={type}
      className={`${variants[variant]} ${className}`.trim()}
      {...props}
    >
      {children}
    </button>
  );
}
