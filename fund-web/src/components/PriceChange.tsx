import React from 'react';
import { formatPercent, getProfitColor } from '../utils/format';

interface PriceChangeProps {
  value: number | null | undefined;
  style?: React.CSSProperties;
}

const PriceChange: React.FC<PriceChangeProps> = ({ value, style }) => {
  return (
    <span style={{ color: getProfitColor(value), fontWeight: 500, ...style }}>
      {formatPercent(value)}
    </span>
  );
};

export default PriceChange;
