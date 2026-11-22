import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, Notification, Order, Payment, User } from './api.service';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  template: `
    <main>
      <h1>devops-setup</h1>
      <p class="muted">Gateway: <code>{{ apiUrl }}</code></p>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      }

      <section>
        <h2>Users</h2>
        <form (ngSubmit)="addUser()">
          <input name="name" placeholder="Name" [(ngModel)]="userName" required />
          <input name="email" type="email" placeholder="Email" [(ngModel)]="userEmail" required />
          <button type="submit">Add user</button>
        </form>
        <ul>
          @for (u of users(); track u.id) {
            <li>#{{ u.id }} {{ u.name }} &lt;{{ u.email }}&gt;</li>
          } @empty {
            <li class="muted">No users yet</li>
          }
        </ul>
      </section>

      <section>
        <h2>Orders</h2>
        <form (ngSubmit)="addOrder()">
          <select name="userId" [(ngModel)]="orderUserId" required>
            <option [ngValue]="null" disabled>User…</option>
            @for (u of users(); track u.id) {
              <option [ngValue]="u.id">{{ u.name }}</option>
            }
          </select>
          <input name="product" placeholder="Product" [(ngModel)]="orderProduct" required />
          <input name="quantity" type="number" min="1" [(ngModel)]="orderQuantity" />
          <input name="price" type="number" min="0.01" step="0.01" [(ngModel)]="orderPrice" />
          <button type="submit">Add order</button>
        </form>
        <ul>
          @for (o of orders(); track o.id) {
            <li>
              {{ o.quantity }} × {{ o.product }} &#64; {{ o.price }} (user #{{ o.userId }}) —
              @if (paymentFor(o.id); as p) {
                <span [class]="p.status === 'APPROVED' ? 'ok' : 'error'">{{ p.status }} {{ p.amount }}</span>
              } @else {
                <span class="muted">payment pending…</span>
              }
            </li>
          } @empty {
            <li class="muted">No orders yet</li>
          }
        </ul>
      </section>

      <section>
        <h2>Notifications <button type="button" (click)="refresh()">Refresh</button></h2>
        <p class="muted">Order → Kafka → payment-service → Kafka → notification-service</p>
        <ul>
          @for (n of notifications(); track n.id) {
            <li><code>{{ n.type }}</code> {{ n.message }} <span class="muted">(order {{ n.orderId.slice(0, 8) }})</span></li>
          } @empty {
            <li class="muted">No notifications yet</li>
          }
        </ul>
      </section>
    </main>
  `,
  styles: `
    main { font-family: system-ui, sans-serif; max-width: 48rem; margin: 2rem auto; padding: 0 1rem; }
    form { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    input, select, button { padding: .4rem .6rem; font: inherit; }
    .muted { color: #666; }
    .error { color: #b00020; }
    .ok { color: #1b7f3b; }
  `,
})
export class App implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly apiUrl = environment.apiUrl;

  protected readonly users = signal<User[]>([]);
  protected readonly orders = signal<Order[]>([]);
  protected readonly payments = signal<Payment[]>([]);
  protected readonly notifications = signal<Notification[]>([]);
  protected readonly error = signal<string | null>(null);

  protected userName = '';
  protected userEmail = '';
  protected orderUserId: number | null = null;
  protected orderProduct = '';
  protected orderQuantity = 1;
  protected orderPrice = 9.99;

  ngOnInit(): void {
    this.refresh();
  }

  protected addUser(): void {
    this.api.createUser({ name: this.userName, email: this.userEmail }).subscribe({
      next: () => {
        this.userName = '';
        this.userEmail = '';
        this.refresh();
      },
      error: (e) => this.fail(e),
    });
  }

  protected addOrder(): void {
    if (this.orderUserId == null) return;
    this.api
      .createOrder({
        userId: this.orderUserId,
        product: this.orderProduct,
        quantity: this.orderQuantity,
        price: this.orderPrice,
      })
      .subscribe({
        next: () => {
          this.orderProduct = '';
          this.refresh();
          // Payment + notifications arrive asynchronously via Kafka.
          setTimeout(() => this.refresh(), 3000);
        },
        error: (e) => this.fail(e),
      });
  }

  protected paymentFor(orderId: string): Payment | undefined {
    return this.payments().find((p) => p.orderId === orderId);
  }

  protected refresh(): void {
    this.error.set(null);
    this.api.listUsers().subscribe({ next: (u) => this.users.set(u), error: (e) => this.fail(e) });
    this.api.listOrders().subscribe({ next: (o) => this.orders.set(o), error: (e) => this.fail(e) });
    this.api.listPayments().subscribe({ next: (p) => this.payments.set(p), error: (e) => this.fail(e) });
    this.api.listNotifications().subscribe({ next: (n) => this.notifications.set(n), error: (e) => this.fail(e) });
  }

  private fail(e: HttpErrorResponse): void {
    this.error.set(`${e.status || 'Network error'}: ${e.error?.message ?? e.message}`);
  }
}
