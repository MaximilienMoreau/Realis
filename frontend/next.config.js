// L'URL de l'API backend est injectée au build (ARG Docker) et doit rester
// autorisée par la CSP, quel que soit le domaine de déploiement réel.
const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/** @type {import('next').NextConfig} */
const nextConfig = {
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          {
            key: "Permissions-Policy",
            value: "camera=self, geolocation=self, microphone=()",
          },
          {
            key: "Content-Security-Policy",
            value: [
              "default-src 'self'",
              "script-src 'self' 'unsafe-eval' 'unsafe-inline'",
              "style-src 'self' 'unsafe-inline'",
              "media-src 'self' blob:",
              `connect-src 'self' ${apiUrl}`,
              "img-src 'self' data:",
            ].join("; "),
          },
        ],
      },
    ];
  },

  output: "standalone",
};

module.exports = nextConfig;
