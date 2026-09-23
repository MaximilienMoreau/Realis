import { test, expect } from "@playwright/test";
const owner = "11111111-1111-4111-8111-111111111111";
const id = "22222222-2222-4222-8222-222222222222";
const result = {id, sha256Hex: "a".repeat(64), fileName: "video.webm", fileSizeBytes: 5,
  mimeType: "video/webm", sealedAt: new Date().toISOString(), tsaTimestamp: new Date().toISOString(),
  tsaUrl: "no-op://localhost", tsaActive: false, geolocLat: null, geolocLng: null, deleted: false, warning: null};

test.beforeEach(async ({page}) => {
  await page.addInitScript(({owner}) => {
    localStorage.setItem("realis_token", "test-token"); localStorage.setItem("realis_user_id", owner); localStorage.setItem("realis_email", "owner@example.test");
  }, {owner});
  await page.route("**/api/policy", route => route.fulfill({json: {version:"2026-09-23", text:"Conditions de test : conservation 365 jours.", retentionDays:365}}));
});

async function seedDraft(page: import("@playwright/test").Page) {
  await page.goto("/nouveau");
  await page.evaluate(async ({owner,id}) => {
    await new Promise<void>((resolve, reject) => {
      const req = indexedDB.open("realis-captures", 1);
      req.onupgradeneeded = () => req.result.createObjectStore("drafts");
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const db = req.result; const tx = db.transaction("drafts","readwrite");
        tx.objectStore("drafts").put({id, owner, blob: new Blob(["video"], {type:"video/webm"}),
          metadata: {mimeType:"video/webm", deviceUa:"test", capturedAt: new Date().toISOString()},
          consent: {geolocConsented:false, policyVersion:"2026-09-23", consentedAt:new Date().toISOString(), retentionDays:365, purposeText:"test"}, savedAt:Date.now()},"pending");
        tx.oncomplete=()=>{db.close();resolve();};
      };
    });
  }, {owner,id});
  await page.reload(); await page.getByRole("button", {name:"Continuer", exact:true}).click();
  await expect(page.getByRole("heading",{name:"Capture prête à envoyer"})).toBeVisible();
}

test("une coupure conserve le brouillon et renvoie le même identifiant", async ({page}) => {
  await seedDraft(page);
  let attempts = 0; const bodies: string[] = [];
  await page.route("**/api/seal", async route => {
    bodies.push(route.request().postData() ?? "");
    if (++attempts === 1) await route.abort("internetdisconnected");
    else await route.fulfill({status:201,json:result});
  });
  await page.getByRole("button",{name:"Sceller cette capture"}).click();
  await expect(page.getByText("Connexion interrompue.", {exact:false})).toBeVisible();
  await page.reload(); await page.getByRole("button",{name:"Continuer", exact:true}).click();
  await page.getByRole("button",{name:"Sceller cette capture"}).click();
  await expect(page.getByText("Télécharger le certificat PDF",{exact:false})).toBeVisible();
  expect(bodies).toHaveLength(2); expect(bodies.every(body=>body.includes(id))).toBeTruthy();
  await page.reload(); await page.getByRole("button",{name:"Continuer", exact:true}).click();
  await expect(page.getByText("Avant de commencer",{exact:true})).toBeVisible();
});

test("une session expirée conserve la vidéo jusqu’à la reconnexion", async ({page}) => {
  await seedDraft(page);
  await page.route("**/api/seal", route=>route.fulfill({status:401,json:{message:"expired"}}));
  await page.getByRole("button",{name:"Sceller cette capture"}).click();
  await expect(page.getByText("Session expirée, veuillez vous reconnecter.",{exact:true})).toBeVisible();
  // Restore credentials as a successful login would, without changing the pending capture.
  await page.evaluate(({owner}) => {localStorage.setItem("realis_token","new-token");localStorage.setItem("realis_user_id",owner);}, {owner});
  await page.reload(); await page.getByRole("button",{name:"Continuer", exact:true}).click();
  await expect(page.getByRole("button",{name:"Télécharger la vidéo avant envoi"})).toBeVisible();
});

test("la caméra refusée propose une reprise accessible", async ({page}) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator.mediaDevices, "getUserMedia", {value: () => Promise.reject(new DOMException("denied","NotAllowedError"))});
  });
  await page.goto("/nouveau"); await page.getByRole("button",{name:"Continuer", exact:true}).click();
  await page.locator('input[type="checkbox"][required]').check();
  await page.getByRole("button",{name:"Accepter et commencer la capture"}).click();
  await page.getByRole("button",{name:"Activer la caméra"}).click();
  await expect(page.getByText("Accès à la caméra refusé.",{exact:false})).toBeVisible();
  await expect(page.getByRole("button",{name:"Réessayer"})).toBeVisible();
});

test("la recherche et le classement préservent la preuve", async ({page}) => {
  await page.route("**/api/seal?**", route=>route.fulfill({json:{content:[{record:result,title:"Cuisine",folder:"Logement A"}], totalPages:1,totalElements:1,number:0}}));
  let labels: unknown;
  await page.route("**/labels", route=>{labels=route.request().postDataJSON();return route.fulfill({status:200});});
  await page.goto("/mes-preuves");
  await expect(page.getByRole("link",{name:"Cuisine",exact:true})).toBeVisible();
  await page.getByRole("button",{name:"Classer / Renommer"}).click();
  await page.getByLabel("Titre",{exact:true}).fill("Cuisine avant travaux");
  await page.getByLabel("Dossier / logement").fill("Logement B");
  await page.getByRole("button",{name:"Enregistrer",exact:true}).click();
  await expect(page.getByRole("heading",{name:"Classer cette preuve"})).toBeHidden();
  expect(labels).toEqual({title:"Cuisine avant travaux",folder:"Logement B"});
});

test("une capture réelle MediaRecorder passe par la prévisualisation", async ({page}) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator.mediaDevices, "getUserMedia", {value: async () => {
      const canvas = document.createElement("canvas"); canvas.width = 160; canvas.height = 120;
      const context = canvas.getContext("2d")!;
      setInterval(() => { context.fillStyle = "#2d52c4"; context.fillRect(0, 0, 160, 120); context.fillStyle = "white"; context.fillText(String(Date.now()), 10, 30); }, 100);
      return canvas.captureStream(10);
    }});
  });
  await page.route("**/api/seal", route => route.fulfill({status:201,json:result}));
  await page.goto("/nouveau"); await page.getByRole("button",{name:"Continuer",exact:true}).click();
  await page.locator('input[type="checkbox"][required]').check();
  await page.getByRole("button",{name:"Accepter et commencer la capture"}).click();
  await page.getByRole("button",{name:"Activer la caméra"}).click();
  await page.getByRole("button",{name:/Démarrer/}).click();
  await expect(page.getByRole("button",{name:/Arrêter \(00:0[1-9]\)/})).toBeVisible();
  await page.getByRole("button",{name:/Arrêter/}).click();
  await expect(page.getByRole("heading",{name:"Capture prête à envoyer"})).toBeVisible();
  await page.getByRole("button",{name:"Sceller cette capture"}).click();
  await expect(page.getByText("Télécharger le certificat PDF",{exact:false})).toBeVisible();
});
