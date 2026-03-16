import { useCallback, useState } from 'react';

const STORAGE_KEY = 'fund_amount_visible';

export function useAmountVisible() {
  const [visible, setVisible] = useState(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored !== 'false';
  });

  const toggle = useCallback(() => {
    setVisible((prev) => {
      const next = !prev;
      localStorage.setItem(STORAGE_KEY, String(next));
      return next;
    });
  }, []);

  const mask = useCallback(
    (value: string) => (visible ? value : '****'),
    [visible],
  );

  return { visible, toggle, mask };
}
