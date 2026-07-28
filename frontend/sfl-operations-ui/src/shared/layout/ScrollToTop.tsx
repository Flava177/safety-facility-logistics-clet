import { useEffect } from 'react';
import { useLocation } from 'react-router';

/** A new screen starts at the top; carrying the previous scroll position over is disorienting. */
const ScrollToTop = () => {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' as ScrollBehavior });
  }, [pathname]);

  return null;
};

export default ScrollToTop;
