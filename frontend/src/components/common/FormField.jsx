import React from 'react';

export default function FormField({ label, required = false, children }) {
  return (
    <label className="field">
      {label}
      {required && (
        <>
          {' '}
          <span className="required">必須</span>
        </>
      )}
      {children}
    </label>
  );
}
