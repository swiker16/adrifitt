import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TaskService } from '../../../core/services/task.service';
import { ClientService } from '../../../core/services/client.service';
import { NotifyService } from '../../../core/services/notify.service';
import {
  ReviewScheduleItem,
  ReviewState,
  SaveTaskRequest,
  TASK_PRIORITY_LABEL,
  TASK_TYPE_LABEL,
  TaskPriority,
  TaskType,
  TrainerTask,
} from '../../../shared/models/task.model';
import { Client } from '../../../shared/models/client.model';

type TaskFilter = 'PENDING' | 'DONE' | 'ALL';

interface TaskForm {
  title: string;
  description: string;
  clientId: number | null;
  type: TaskType;
  priority: TaskPriority;
  dueDate: string;
}

@Component({
  selector: 'app-trainer-tasks',
  imports: [DatePipe, LowerCasePipe, RouterLink, FormsModule, MatIconModule],
  templateUrl: './trainer-tasks.html',
  styleUrl: './trainer-tasks.scss',
})
export class TrainerTasks {
  private readonly taskService = inject(TaskService);
  private readonly clientService = inject(ClientService);
  private readonly notify = inject(NotifyService);

  readonly typeLabel = TASK_TYPE_LABEL;
  readonly priorityLabel = TASK_PRIORITY_LABEL;
  readonly types = Object.keys(TASK_TYPE_LABEL) as TaskType[];
  readonly priorities = Object.keys(TASK_PRIORITY_LABEL) as TaskPriority[];

  // Review schedule
  readonly schedule = signal<ReviewScheduleItem[]>([]);
  readonly scheduleLoading = signal(true);
  readonly scheduleError = signal(false);

  readonly overdueCount = computed(() => this.schedule().filter((s) => s.state === 'OVERDUE').length);
  readonly dueSoonCount = computed(() => this.schedule().filter((s) => s.state === 'DUE_SOON').length);

  // Tasks
  readonly tasks = signal<TrainerTask[]>([]);
  readonly tasksLoading = signal(true);
  readonly tasksError = signal(false);
  readonly filter = signal<TaskFilter>('PENDING');
  readonly busyId = signal<number | null>(null);

  readonly clients = signal<Client[]>([]);

  // Modal
  readonly modalOpen = signal(false);
  readonly editing = signal<TrainerTask | null>(null);
  readonly saving = signal(false);
  readonly submitted = signal(false);
  form: TaskForm = this.emptyForm();

  readonly runningJobs = signal(false);

  constructor() {
    this.loadSchedule();
    this.loadTasks();
    this.clientService.findAll().subscribe({
      next: (c) => this.clients.set([...c].sort((a, b) => a.firstName.localeCompare(b.firstName))),
      error: () => this.clients.set([]),
    });
  }

  loadSchedule(): void {
    this.scheduleLoading.set(true);
    this.scheduleError.set(false);
    this.taskService.reviewSchedule().subscribe({
      next: (s) => {
        this.schedule.set(s);
        this.scheduleLoading.set(false);
      },
      error: () => {
        this.scheduleError.set(true);
        this.scheduleLoading.set(false);
      },
    });
  }

  setFilter(f: TaskFilter): void {
    this.filter.set(f);
    this.loadTasks();
  }

  loadTasks(): void {
    this.tasksLoading.set(true);
    this.tasksError.set(false);
    const f = this.filter();
    this.taskService.findAll({ status: f === 'ALL' ? null : f }).subscribe({
      next: (t) => {
        this.tasks.set(t);
        this.tasksLoading.set(false);
      },
      error: () => {
        this.tasksError.set(true);
        this.tasksLoading.set(false);
      },
    });
  }

  daysText(item: ReviewScheduleItem): string {
    const d = item.daysUntil;
    if (d < 0) return `Vencida hace ${-d} ${-d === 1 ? 'día' : 'días'}`;
    if (d === 0) return 'Hoy';
    return `En ${d} ${d === 1 ? 'día' : 'días'}`;
  }

  stateBadge(state: ReviewState): string {
    return state === 'OVERDUE' ? 'red' : state === 'DUE_SOON' ? 'orange' : 'gray';
  }

  stateLabel(state: ReviewState): string {
    return state === 'OVERDUE' ? 'Vencida' : state === 'DUE_SOON' ? 'Próxima' : 'Programada';
  }

  priorityBadge(p: TaskPriority): string {
    return p === 'HIGH' ? 'red' : p === 'MEDIUM' ? 'orange' : 'gray';
  }

  toggle(task: TrainerTask): void {
    if (this.busyId()) return;
    this.busyId.set(task.id);
    const req = task.status === 'DONE' ? this.taskService.reopen(task.id) : this.taskService.complete(task.id);
    req.subscribe({
      next: (updated) => {
        this.busyId.set(null);
        const f = this.filter();
        if (f !== 'ALL' && updated.status !== f) {
          this.tasks.update((list) => list.filter((t) => t.id !== updated.id));
        } else {
          this.tasks.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
        }
        this.notify.success(updated.status === 'DONE' ? 'Tarea completada.' : 'Tarea reabierta.');
      },
      error: (err) => {
        this.busyId.set(null);
        this.notify.error(err, 'No se pudo actualizar la tarea.');
      },
    });
  }

  remove(task: TrainerTask): void {
    if (!confirm(`¿Eliminar la tarea "${task.title}"?`)) return;
    this.taskService.delete(task.id).subscribe({
      next: () => {
        this.tasks.update((list) => list.filter((t) => t.id !== task.id));
        this.notify.success('Tarea eliminada.');
      },
      error: (err) => this.notify.error(err, 'No se pudo eliminar la tarea.'),
    });
  }

  openNew(): void {
    this.editing.set(null);
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.modalOpen.set(true);
  }

  openEdit(task: TrainerTask): void {
    this.editing.set(task);
    this.form = {
      title: task.title,
      description: task.description ?? '',
      clientId: task.clientId,
      type: task.type,
      priority: task.priority,
      dueDate: task.dueDate ?? '',
    };
    this.submitted.set(false);
    this.modalOpen.set(true);
  }

  closeModal(): void {
    if (this.saving()) return;
    this.modalOpen.set(false);
  }

  save(): void {
    this.submitted.set(true);
    const title = this.form.title.trim();
    if (!title) return;
    const request: SaveTaskRequest = {
      title,
      description: this.form.description.trim() || null,
      clientId: this.form.clientId || null,
      type: this.form.type,
      priority: this.form.priority,
      dueDate: this.form.dueDate || null,
    };
    const editing = this.editing();
    this.saving.set(true);
    const req = editing ? this.taskService.update(editing.id, request) : this.taskService.create(request);
    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.notify.success(editing ? 'Tarea actualizada.' : 'Tarea creada.');
        this.loadTasks();
      },
      error: (err) => {
        this.saving.set(false);
        this.notify.error(err, 'No se pudo guardar la tarea.');
      },
    });
  }

  runDailyJobs(): void {
    this.runningJobs.set(true);
    this.taskService.runDailyJobs().subscribe({
      next: (r) => {
        this.runningJobs.set(false);
        this.notify.success(
          `Tareas diarias ejecutadas: ${r.subscriptionsProcessed} suscripciones procesadas, ${r.reviewTasksCreated} tareas de revisión creadas.`,
        );
        this.loadSchedule();
        this.loadTasks();
      },
      error: (err) => {
        this.runningJobs.set(false);
        this.notify.error(err, 'No se pudieron ejecutar las tareas diarias.');
      },
    });
  }

  private emptyForm(): TaskForm {
    return { title: '', description: '', clientId: null, type: 'TASK', priority: 'MEDIUM', dueDate: '' };
  }
}
