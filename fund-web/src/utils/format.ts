export function formatAmount(value: number | null | undefined): string {
  if (value == null) return '--';
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function formatPercent(value: number | null | undefined): string {
  if (value == null) return '--';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

export function formatNav(value: number | null | undefined): string {
  if (value == null) return '--';
  return value.toFixed(4);
}

export function getProfitColor(value: number | null | undefined): string {
  if (value == null || value === 0) return '#1F2937';
  return value > 0 ? '#F5222D' : '#52C41A';
}

export const FUND_TYPE_MAP: Record<string, string> = {
  STOCK: '股票型',
  MIXED: '混合型',
  BOND: '债券型',
  MONEY: '货币型',
  QDII: 'QDII',
  INDEX: '指数型',
};

export const FUND_TYPE_COLOR: Record<string, string> = {
  STOCK: '#1677FF',
  MIXED: '#722ED1',
  BOND: '#13C2C2',
  MONEY: '#52C41A',
  QDII: '#F5222D',
  INDEX: '#FA8C16',
};

export const FUND_TYPE_TAG_COLOR: Record<string, string> = {
  STOCK: 'blue',
  MIXED: 'purple',
  BOND: 'cyan',
  MONEY: 'green',
  QDII: 'red',
  INDEX: 'orange',
};

export function getFundTypeColor(type: string | null | undefined): string {
  if (!type) return '#8C8C8C';
  return FUND_TYPE_COLOR[type] || '#8C8C8C';
}

export function formatFundType(type: string | null | undefined): string {
  if (!type) return '--';
  return FUND_TYPE_MAP[type] || type;
}
