import { useState } from "react";
import { useParentalStore } from "@app/stores/parental";

export function PinDialog({ onClose }: { onClose: () => void }) {
  const unlock = useParentalStore((s) => s.unlock);
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const ok = await unlock(pin);
    if (ok) onClose();
    else setError("Wrong PIN");
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h3>Enter PIN</h3>
        <input
          autoFocus
          type="password"
          inputMode="numeric"
          value={pin}
          onChange={(e) => setPin(e.target.value)}
        />
        {error && <div className="banner error">{error}</div>}
        <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
          <button type="button" onClick={onClose}>Cancel</button>
          <button type="submit">Unlock</button>
        </div>
      </form>
    </div>
  );
}
