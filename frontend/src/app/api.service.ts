import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../environments/environment';

export interface User {
  id: number;
  name: string;
  email: string;
  createdAt: string;
}

export interface Order {
  id: string;
  userId: number;
  product: string;
  quantity: number;
  price: number;
  createdAt: string;
}

export interface Payment {
  id: number;
  orderId: string;
  userId: number;
  amount: number;
  status: 'APPROVED' | 'DECLINED';
  processedAt: string;
}

export interface Notification {
  id: string;
  type: 'ORDER_CREATED' | 'PAYMENT_PROCESSED';
  orderId: string;
  userId: number;
  message: string;
  receivedAt: string;
}

/** All calls go through the API gateway. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listUsers() {
    return this.http.get<User[]>(`${this.base}/api/users`);
  }

  createUser(body: { name: string; email: string }) {
    return this.http.post<User>(`${this.base}/api/users`, body);
  }

  listOrders() {
    return this.http.get<Order[]>(`${this.base}/api/orders`);
  }

  createOrder(body: { userId: number; product: string; quantity: number; price: number }) {
    return this.http.post<Order>(`${this.base}/api/orders`, body);
  }

  listPayments() {
    return this.http.get<Payment[]>(`${this.base}/api/payments`);
  }

  listNotifications() {
    return this.http.get<Notification[]>(`${this.base}/api/notifications`);
  }
}
