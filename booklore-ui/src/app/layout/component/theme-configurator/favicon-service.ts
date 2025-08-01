import {Injectable} from '@angular/core';

@Injectable({providedIn: 'root'})
export class FaviconService {
  private svgTemplate = (color: string) => `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 126 126" fill="currentColor">
      <path d="M59 4.79297C71.5051 11.5557 80 24.7854 80 40C80 40.5959 79.987 41.1888 79.9609 41.7783C79.8609 44.0406 81.7355 46 84 46C106.091 46 124 63.9086 124 86C124 108.091 106.091 126 84 126H10C4.47715 126 0 121.523 0 116V39.0068L0.0126953 38.9941C0.357624 25.0252 7.86506 12.8347 19 5.95215V63.832C19 64.8345 20.0676 65.4391 20.9121 64.9902L21.0771 64.8867L38.2227 52.3428C38.6819 52.0068 39.3064 52.0068 39.7656 52.3428L56.9229 64.8945L57.0879 64.998C57.9324 65.447 59 64.8423 59 63.8398V4.79297Z" fill="${color}"/>
      <path d="M40 0C43.8745 0 47.6199 0.552381 51.1631 1.58008V50.9697L44.3926 46.0176L44.0879 45.8037C40.9061 43.6679 36.7098 43.7393 33.5957 46.0176L26.8369 50.9619V2.21875C30.9593 0.782634 35.3881 0 40 0Z" fill="white"/>
    </svg>
  `;

  updateFavicon(color: string) {
    const svg = this.svgTemplate(color);
    const blob = new Blob([svg], {type: 'image/svg+xml'});
    const url = URL.createObjectURL(blob);

    let favicon = document.querySelector("link[rel*='icon']") as HTMLLinkElement;
    if (!favicon) {
      favicon = document.createElement('link');
      favicon.rel = 'icon';
      document.head.appendChild(favicon);
    }

    favicon.type = 'image/svg+xml';
    favicon.href = url;
  }
}
