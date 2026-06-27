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
              "connect-src 'self' http://localhost:8080 https://api.realis.app",
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
