# Janery — frontend

Aplicación Angular 20 con componentes standalone y Tailwind CSS 4. La guía general está en el [README raíz](../README.md); la estructura y las convenciones, en [docs/frontend.md](../docs/frontend.md) y [docs/diseno.md](../docs/diseno.md).

| Comando | Qué hace |
|---|---|
| `npm install` | Instala las dependencias |
| `npm start` | Servidor de desarrollo en http://localhost:4200, contra el backend en http://localhost:8080 |
| `npm run simulador` | Frontend del simulador en http://localhost:4201, contra el backend en el puerto 8081 |
| `npm run build` | Versión de producción en `dist/frontend/browser`; llama a `/api/v1` en el mismo dominio |
| `npm run build:simulador` | Versión del simulador publicado en `dist/simulador/browser` |
| `npx ng test --watch=false --browsers=ChromeHeadless` | Pruebas unitarias |

La dirección de la API de cada versión está en `src/environments/`. Esos archivos llegan al navegador: nunca poner secretos en ellos.
