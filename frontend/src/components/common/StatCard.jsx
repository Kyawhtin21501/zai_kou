import React from 'react';
import { ArrowUpRight } from 'lucide-react';

export default function StatCard({
  label,
  value,
  unit,
  icon: Icon,
  color,
  caption,
  onClick,
}) {
  return (
    <article className="stat-card">
      <div className="flex items-center justify-between">
        <span className="stat-label">{label}</span>
        <span className={`stat-icon ${color}`}>
          <Icon size={18} />
        </span>
      </div>
      <div className="stat-value">
        {value}
        <span>{unit}</span>
      </div>
      {onClick ? (
        <button type="button" onClick={onClick} className="stat-link">
          {caption}
          <ArrowUpRight size={14} />
        </button>
      ) : (
        <p className="stat-caption">{caption}</p>
      )}
    </article>
  );
}
