"use strict";
// Chiffrement des secrets (identifiants des sources) via safeStorage.
// Formats : "enc:v1:<base64>" (chiffre par l'OS) ou "plain:v1:<base64>" quand
// safeStorage est indisponible (ou sur Linux avec le backend "basic_text", qui
// n'apporte aucune protection reelle). `secure` permet a l'UI d'avertir.

function createSecrets(safeStorage) {
  function isSecure() {
    try {
      if (!safeStorage || !safeStorage.isEncryptionAvailable()) return false;
      if (typeof safeStorage.getSelectedStorageBackend === "function") {
        if (safeStorage.getSelectedStorageBackend() === "basic_text") return false;
      }
      return true;
    } catch {
      return false;
    }
  }

  function encrypt(plain) {
    if (typeof plain !== "string") throw new TypeError("string attendu");
    if (isSecure()) {
      return "enc:v1:" + safeStorage.encryptString(plain).toString("base64");
    }
    return "plain:v1:" + Buffer.from(plain, "utf8").toString("base64");
  }

  function decrypt(cipher) {
    if (typeof cipher !== "string") throw new TypeError("string attendu");
    if (cipher.startsWith("enc:v1:")) {
      if (!isSecure()) throw new Error("secure-storage-unavailable");
      return safeStorage.decryptString(Buffer.from(cipher.slice(7), "base64"));
    }
    if (cipher.startsWith("plain:v1:")) {
      return Buffer.from(cipher.slice(9), "base64").toString("utf8");
    }
    throw new Error("unknown-format");
  }

  return { encrypt, decrypt, isSecure };
}

module.exports = { createSecrets };
