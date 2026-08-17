import { Client, IMessage, IFrame } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const USE_STREAM_AFFINITY = import.meta.env.VITE_USE_STREAM_AFFINITY === "true";

export class StreamStompClient {
  private client: Client;
  private wsBaseUrl: string;
  private streamKey?: string;
  private connectHeaders: Record<string, string> = {};

  constructor(wsBaseUrl: string, streamKey?: string) {
    this.wsBaseUrl = wsBaseUrl;
    this.streamKey = streamKey;
    this.client = new Client({
      debug: (msg) => console.debug('[STOMP]', msg),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });
  }

  setAuthToken(token: string): void {
    this.connectHeaders = { Authorization: `Bearer ${token}` };
    this.client.configure({ connectHeaders: this.connectHeaders });
  }

  async connect(): Promise<void> {
    if (!this.client.webSocketFactory) {
      this.client.configure({ 
        webSocketFactory: () => this.buildSocket(),
        connectHeaders: this.connectHeaders,
      });
    }
    return new Promise((resolve, reject) => {
      this.client.onConnect = () => resolve();
      this.client.onStompError = (frame: IFrame) => reject(frame.headers['message']);
      this.client.activate();
    });
  }

  /**
   * Pick the transport for this connection. With VITE_USE_STREAM_AFFINITY=true
   * a native WebSocket to /ws-chat/stream/{streamKey} is used so the path
   * carries the stream key for consistent-hash load balancing; otherwise the
   * legacy SockJS /ws-chat endpoint is used unchanged. The factory closure
   * captures streamKey, so every STOMP reconnect rebuilds the same keyed URL.
   */
  private buildSocket(): WebSocket {
    if (USE_STREAM_AFFINITY) {
      return new WebSocket(buildStreamAffinityUrl(this.wsBaseUrl, this.streamKey));
    }
    return new SockJS(buildSockJsUrl(this.wsBaseUrl));
  }

  disconnect(): void { this.client.deactivate(); }

  subscribe<T>(destination: string, callback: (payload: T) => void): () => void {
    const sub = this.client.subscribe(destination, (msg: IMessage) => callback(JSON.parse(msg.body)));
    return () => sub.unsubscribe();
  }

  publish(destination: string, payload: Record<string, any>): void {
    if (this.client.connected) {
      this.client.publish({ destination, body: JSON.stringify(payload) });
    }
  }

  isConnected(): boolean {
    return this.client.connected;
  }
}

export function buildSockJsUrl(baseUrl: string): string {
  let trimmed = baseUrl.trim().replace(/\/+$/, "");
  // SockJS uses HTTP transport, not WebSocket — convert ws(s):// to http(s)://
  trimmed = trimmed.replace(/^ws:\/\//, "http://").replace(/^wss:\/\//, "https://");
  if (!trimmed || trimmed === ".") {
    return "/ws-chat";
  }
  if (trimmed.endsWith("/ws-chat")) {
    return trimmed;
  }
  return `${trimmed}/ws-chat`;
}

export function buildStreamAffinityUrl(baseUrl: string, streamKey?: string): string {
  if (!streamKey) {
    throw new Error(
      "VITE_USE_STREAM_AFFINITY requires a streamKey to connect to /ws-chat/stream/{streamKey}",
    );
  }
  let base = baseUrl.trim().replace(/\/+$/, "");
  // A relative or empty base cannot form an absolute native WebSocket URL —
  // derive the origin from the page (dev proxying through Vite).
  if (!base || base === "." || base.startsWith("/")) {
    base = `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}`;
  }
  if (base.startsWith("https://")) {
    base = `wss://${base.slice("https://".length)}`;
  } else if (base.startsWith("http://")) {
    base = `ws://${base.slice("http://".length)}`;
  }
  if (!base.startsWith("ws://") && !base.startsWith("wss://")) {
    throw new Error(`Cannot build a WebSocket URL from base '${baseUrl}'`);
  }
  const basePath = base.endsWith("/ws-chat") ? base : `${base}/ws-chat`;
  return `${basePath}/stream/${streamKey}`;
}
