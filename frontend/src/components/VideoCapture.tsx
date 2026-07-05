"use client";

import { useEffect, useRef, useState } from "react";

export interface CaptureMetadata {
  capturedAt: string;
  mimeType: string;
  deviceUa: string;
  geolocLat?: number;
  geolocLng?: number;
}

interface Props {
  geolocConsented: boolean;
  onCaptured: (blob: Blob, metadata: CaptureMetadata) => void;
}

type CaptureState = "idle" | "requesting" | "preview" | "recording" | "recorded" | "error";

function resolveMimeType(): string {
  for (const mime of ["video/webm;codecs=vp9", "video/webm", "video/mp4"]) {
    if (typeof MediaRecorder !== "undefined" && MediaRecorder.isTypeSupported(mime)) {
      return mime;
    }
  }
  return "video/webm";
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60).toString().padStart(2, "0");
  const s = (seconds % 60).toString().padStart(2, "0");
  return `${m}:${s}`;
}

export default function VideoCapture({ geolocConsented, onCaptured }: Props) {
  const [state, setState]       = useState<CaptureState>("idle");
  const [duration, setDuration] = useState(0);
  const [errorMsg, setErrorMsg] = useState("");
  const [facingBack, setFacingBack] = useState(true);

  const videoRef    = useRef<HTMLVideoElement>(null);
  const streamRef   = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef   = useRef<BlobPart[]>([]);
  const timerRef    = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    return () => {
      stopStream();
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const stopStream = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  };

  const activateCamera = async (useBack: boolean = facingBack) => {
    setState("requesting");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: useBack ? "environment" : "user" },
        audio: false,
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.play().catch(() => {});
      }
      setState("preview");
    } catch (err) {
      setErrorMsg(
        err instanceof DOMException && err.name === "NotAllowedError"
          ? "Accès à la caméra refusé. Vérifiez les permissions du navigateur."
          : "Impossible d'accéder à la caméra."
      );
      setState("error");
    }
  };

  const startRecording = () => {
    if (!streamRef.current) return;
    const mimeType = resolveMimeType();
    const recorder = new MediaRecorder(streamRef.current, { mimeType });
    chunksRef.current = [];

    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunksRef.current.push(e.data);
    };

    recorder.onstop = async () => {
      stopStream();
      if (timerRef.current) clearInterval(timerRef.current);

      const blob = new Blob(chunksRef.current, { type: mimeType });
      const metadata: CaptureMetadata = {
        capturedAt: new Date().toISOString(),
        mimeType,
        deviceUa: navigator.userAgent,
      };

      if (geolocConsented && navigator.geolocation) {
        try {
          const pos = await new Promise<GeolocationPosition>((res, rej) =>
            navigator.geolocation.getCurrentPosition(res, rej, { timeout: 5000 })
          );
          metadata.geolocLat = pos.coords.latitude;
          metadata.geolocLng = pos.coords.longitude;
        } catch {
          // Géoloc refusée ou timeout : on continue sans
        }
      }

      setState("recorded");
      onCaptured(blob, metadata);
    };

    recorder.start(1000);
    recorderRef.current = recorder;
    setDuration(0);
    timerRef.current = setInterval(() => setDuration((d) => d + 1), 1000);
    setState("recording");
  };

  const stopRecording = () => {
    recorderRef.current?.stop();
  };

  const retake = () => {
    setDuration(0);
    setState("idle");
    chunksRef.current = [];
  };

  const toggleCamera = async () => {
    const next = !facingBack;
    setFacingBack(next);
    stopStream();
    setState("idle");
    // On passe `next` explicitement : `activateCamera` par défaut lirait `facingBack`
    // depuis la closure de ce rendu, donc encore l'ancienne valeur au moment du timeout.
    setTimeout(() => activateCamera(next), 200);
  };

  return (
    <div className="space-y-4">
      <div className="text-center space-y-1">
        <h2 className="text-lg font-semibold text-realis-700 dark:text-realis-300">Capturer l&apos;état des lieux</h2>
        <p className="text-sm text-gray-500 dark:text-gray-400">
          La vidéo sera scellée dès l&apos;arrêt de l&apos;enregistrement.
        </p>
      </div>

      {/* Viewfinder : fond noir intentionnel, pas de dark override */}
      <div className="relative bg-black rounded-2xl overflow-hidden aspect-video">
        <video
          ref={videoRef}
          autoPlay
          muted
          playsInline
          className="w-full h-full object-cover"
        />

        {state === "recording" && (
          <div className="absolute top-3 left-3 flex items-center gap-2
                          bg-black/60 text-white text-xs rounded-full px-3 py-1.5">
            <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse" />
            {formatDuration(duration)}
          </div>
        )}

        {(state === "preview" || state === "recording") && (
          <button
            type="button"
            onClick={toggleCamera}
            disabled={state === "recording"}
            className="absolute top-3 right-3 bg-black/60 text-white rounded-full p-2
                       disabled:opacity-40 hover:bg-black/80 transition-colors"
            title="Retourner la caméra"
          >
            ↺
          </button>
        )}

        {(state === "idle" || state === "requesting" || state === "recorded") && (
          <div className="absolute inset-0 flex items-center justify-center text-gray-600">
            {state === "requesting" && (
              <span className="text-white text-sm animate-pulse">Activation caméra…</span>
            )}
            {state === "recorded" && (
              <span className="text-white text-sm">✓ Capture terminée</span>
            )}
          </div>
        )}
      </div>

      {state === "error" && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 rounded-xl p-3 text-sm">
          {errorMsg}
        </div>
      )}

      <div className="flex gap-3">
        {state === "idle" && (
          <button
            type="button"
            onClick={() => activateCamera()}
            className="flex-1 py-3 bg-realis-600 hover:bg-realis-700 text-white font-medium rounded-xl transition-colors"
          >
            Activer la caméra
          </button>
        )}

        {state === "preview" && (
          <button
            type="button"
            onClick={startRecording}
            className="flex-1 py-3 bg-red-600 hover:bg-red-700 text-white font-medium rounded-xl transition-colors"
          >
            ● Démarrer l&apos;enregistrement
          </button>
        )}

        {state === "recording" && (
          <button
            type="button"
            onClick={stopRecording}
            className="flex-1 py-3 bg-gray-800 hover:bg-gray-900 dark:bg-gray-700 dark:hover:bg-gray-600 text-white font-medium rounded-xl transition-colors"
          >
            ■ Arrêter ({formatDuration(duration)})
          </button>
        )}

        {state === "recorded" && (
          <button
            type="button"
            onClick={retake}
            className="flex-1 py-3 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 font-medium rounded-xl transition-colors"
          >
            Recommencer
          </button>
        )}
      </div>

      {!geolocConsented && (
        <p className="text-xs text-gray-400 dark:text-gray-500 text-center">
          Géolocalisation désactivée (non consentie).
        </p>
      )}
    </div>
  );
}
