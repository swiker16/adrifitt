import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { roleGuard } from './core/guards/role-guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'trainer',
    canActivate: [authGuard, roleGuard(['TRAINER'])],
    loadComponent: () =>
      import('./layouts/trainer-layout/trainer-layout').then((m) => m.TrainerLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/trainer/dashboard/trainer-dashboard').then((m) => m.TrainerDashboard),
      },
      {
        path: 'clients',
        loadComponent: () =>
          import('./features/trainer/clients/clients-list').then((m) => m.ClientsList),
      },
      {
        path: 'clients/:id',
        loadComponent: () =>
          import('./features/trainer/clients/client-detail').then((m) => m.ClientDetail),
      },
      {
        path: 'reports',
        loadComponent: () =>
          import('./features/trainer/reports/trainer-reports').then((m) => m.TrainerReports),
      },
      {
        path: 'plans',
        loadComponent: () => import('./features/trainer/plans/plan-list').then((m) => m.PlanList),
      },
      {
        path: 'plans/new',
        loadComponent: () => import('./features/trainer/plans/plan-form').then((m) => m.PlanForm),
      },
      {
        path: 'plans/:id/edit',
        loadComponent: () => import('./features/trainer/plans/plan-form').then((m) => m.PlanForm),
      },
      {
        path: 'plans/:id',
        loadComponent: () =>
          import('./features/trainer/plans/plan-details').then((m) => m.PlanDetails),
      },
      {
        path: 'diets',
        loadComponent: () =>
          import('./features/trainer/diets/diet-list').then((m) => m.DietList),
      },
      {
        path: 'diets/new',
        loadComponent: () =>
          import('./features/trainer/diets/diet-form').then((m) => m.DietForm),
      },
      {
        path: 'diets/:id/edit',
        loadComponent: () =>
          import('./features/trainer/diets/diet-form').then((m) => m.DietForm),
      },
      {
        path: 'diets/:id',
        loadComponent: () =>
          import('./features/trainer/diets/diet-details').then((m) => m.DietDetails),
      },
      {
        path: 'workouts',
        loadComponent: () =>
          import('./features/trainer/workouts/workout-list').then((m) => m.WorkoutList),
      },
      {
        path: 'workouts/new',
        loadComponent: () =>
          import('./features/trainer/workouts/workout-form').then((m) => m.WorkoutForm),
      },
      {
        path: 'workouts/:id/edit',
        loadComponent: () =>
          import('./features/trainer/workouts/workout-form').then((m) => m.WorkoutForm),
      },
      {
        path: 'workouts/:id',
        loadComponent: () =>
          import('./features/trainer/workouts/workout-details').then((m) => m.WorkoutDetails),
      },
    ],
  },
  {
    path: 'client',
    canActivate: [authGuard, roleGuard(['CLIENT'])],
    loadComponent: () =>
      import('./layouts/client-layout/client-layout').then((m) => m.ClientLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/client/dashboard/client-dashboard').then((m) => m.ClientDashboard),
      },
      {
        path: 'report',
        loadComponent: () =>
          import('./features/client/report/client-report').then((m) => m.ClientReport),
      },
      {
        path: 'messages',
        loadComponent: () =>
          import('./features/client/messages/client-messages').then((m) => m.ClientMessages),
      },
      {
        path: 'analyses',
        loadComponent: () =>
          import('./features/client/analyses/client-analyses').then((m) => m.ClientAnalyses),
      },
      {
        path: 'diet',
        loadComponent: () =>
          import('./features/client/diet/client-diet').then((m) => m.ClientDietView),
      },
      {
        path: 'workout',
        loadComponent: () =>
          import('./features/client/workout/client-workout').then((m) => m.ClientWorkoutView),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
