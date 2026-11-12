import { afterEach, describe, expect, it } from "vitest";
import { RateLimitedError, TokenRejectedError, setCloudHttp, sportsTeamGet, type HttpRequest } from "./client";

const reply = (status: number, body = "") => async () => ({ status, text: body, retryAfter: "7", etag: "" });

describe("sportsTeamGet (écussons via le Worker)", () => {
  afterEach(() => setCloudHttp(null));

  it("envoie seulement le nom d'équipe, avec le jeton d'appareil", async () => {
    let seen: HttpRequest | null = null;
    setCloudHttp(async (r) => { seen = r; return { status: 200, text: '{"team":{"name":"Club Nord","badge":"https://img.example/n.png"}}', retryAfter: "", etag: "" }; });
    const body = await sportsTeamGet("https://w.example", "tok", "Club Nord & Co");
    expect(body).toEqual({ team: { name: "Club Nord", badge: "https://img.example/n.png" } });
    expect(seen!.url).toBe("https://w.example/api/sports/team?name=Club%20Nord%20%26%20Co");
    expect(seen!.headers?.authorization).toBe("Bearer tok");
  });

  it("nom refusé (404) = équipe inconnue ; service absent (503) = rien", async () => {
    setCloudHttp(reply(404));
    expect(await sportsTeamGet("https://w.example", "t", "X Y")).toEqual({ team: null });
    setCloudHttp(reply(503));
    expect(await sportsTeamGet("https://w.example", "t", "X Y")).toBeNull();
  });

  it("jeton révoqué et débit limité : erreurs (rien n'est mémorisé)", async () => {
    setCloudHttp(reply(401));
    await expect(sportsTeamGet("https://w.example", "t", "X Y")).rejects.toBeInstanceOf(TokenRejectedError);
    setCloudHttp(reply(429));
    await expect(sportsTeamGet("https://w.example", "t", "X Y")).rejects.toBeInstanceOf(RateLimitedError);
  });
});
