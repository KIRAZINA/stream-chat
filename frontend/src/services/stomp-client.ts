import { Client, IMessage, IFrame } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export class StreamStompClient {
  private client: Client;
  private wsBaseUrl: string;
  private connectHeaders: Record<string, string> = {};

  constructor(wsBaseUrl: string) {
    this.wsBaseUrl = wsBaseUrl;
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
        webSocketFactory: () => new SockJS(`${this.wsBaseUrl}/ws-chat`),
        connectHeaders: this.connectHeaders,
      });
    }
    return new Promise((resolve, reject) => {
      this.client.onConnect = () => resolve();
      this.client.onStompError = (frame: IFrame) => reject(frame.headers['message']);
      this.client.activate();
    });
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
