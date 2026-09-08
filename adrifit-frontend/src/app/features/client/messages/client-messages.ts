import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

interface Message {
  from: string;
  initials: string;
  text: string;
  time: string;
  mine: boolean;
}

@Component({
  selector: 'app-client-messages',
  imports: [MatIconModule],
  templateUrl: './client-messages.html',
  styleUrl: './client-messages.scss',
})
export class ClientMessages {
  // Mock data for the initial visual version.
  readonly messages: Message[] = [
    { from: 'Coach Adri', initials: 'CA', text: '¡Hola! ¿Cómo ha ido la semana?', time: 'Lun 09:12', mine: false },
    { from: 'Tú', initials: 'TU', text: 'Muy bien, he cumplido la dieta casi al 100%.', time: 'Lun 10:30', mine: true },
    { from: 'Coach Adri', initials: 'CA', text: 'Genial. Sube un poco la proteína en la cena.', time: 'Lun 11:05', mine: false },
    { from: 'Coach Adri', initials: 'CA', text: 'Recuerda enviar tu reporte antes del domingo.', time: 'Mar 18:40', mine: false },
  ];
}
