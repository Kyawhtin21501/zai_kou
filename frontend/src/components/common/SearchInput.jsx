import React from 'react';
import { Search, X } from 'lucide-react';

export default function SearchInput({
  label,
  value,
  onChange,
  placeholder,
  clearLabel = '検索をクリア',
}) {
  return (
    <div className="search-field">
      <Search size={18} />
      <input
        aria-label={label}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label={clearLabel}
        >
          <X size={15} />
        </button>
      )}
    </div>
  );
}
