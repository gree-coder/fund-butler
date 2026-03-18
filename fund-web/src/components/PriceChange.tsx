import React from 'react';
import { formatPercent, getProfitColor } from '../utils/format';

interface PriceChangeProps {
  value: number | null | undefined;
  style?: React.CSSProperties;
  showBg?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

const sizeMap = { sm: 12, md: 14, lg: 16 };

const PriceChange: React.FC<PriceChangeProps> = ({ value, style, showBg, size = 'md' }) => {
  const color = getProfitColor(value);
  const fontSize = sizeMap[size];

  if (showBg) {
    const bgClass = value == null || value === 0
      ? 'fund-price-bg fund-price-bg--neutral'
      : value > 0
        ? 'fund-price-bg fund-price-bg--profit'
        : 'fund-price-bg fund-price-bg--loss';
    return (
      <span className={bgClass} style={{ fontSize, ...style }}>
        {formatPercent(value)}
      </span>
    );
  }

  return (
    <span style={{ color, fontWeight: 500, fontSize, ...style }}>
      {formatPercent(value)}
    </span>
  );
};

export default PriceChange;
