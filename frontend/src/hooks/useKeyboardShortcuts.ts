import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMultiProject } from '../context/MultiProjectContext';

export function useKeyboardShortcuts() {
  const navigate = useNavigate();
  const { recentSessionOrder, openProjectIds, activeSessionByProject } = useMultiProject();

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      const mod = e.metaKey || e.ctrlKey;
      if (!mod || e.key !== 'Tab') return;

      e.preventDefault();
      if (recentSessionOrder.length < 2) return;

      const currentId = recentSessionOrder[0]!;
      const direction = e.shiftKey ? 1 : -1;
      const currentIdx = recentSessionOrder.indexOf(currentId);
      const nextIdx = (currentIdx + direction + recentSessionOrder.length) % recentSessionOrder.length;
      const nextSessionId = recentSessionOrder[nextIdx]!;
      if (!nextSessionId) return;

      for (const pid of openProjectIds) {
        if (activeSessionByProject[pid] === nextSessionId) {
          navigate(`/projects/${pid}/sessions/${nextSessionId}`);
          return;
        }
      }

      fetch(`/api/sessions/${nextSessionId}`)
        .then((res) => res.json())
        .then((data) => {
          if (data.projectId) {
            navigate(`/projects/${data.projectId}/sessions/${nextSessionId}`);
          }
        })
        .catch(() => {});
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [recentSessionOrder, openProjectIds, activeSessionByProject, navigate]);
}
