// Remplace « cloudflare:workers » dans le bundle du secours : la classe Guard (Durable Object) n'est jamais instanciée.
export class DurableObject { constructor(state, env) { this.ctx = state; this.env = env; } }
export const env = {};
