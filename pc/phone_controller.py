import tkinter as tk
from tkinter import ttk, messagebox

import serial
import serial.tools.list_ports


class App:
    def __init__(self, root):
        self.root = root
        self.ser = None
        root.title("PhoneBT Controller")
        root.geometry("340x330")

        # اختيار منفذ COM
        top = ttk.Frame(root, padding=10)
        top.pack(fill="x")
        ttk.Label(top, text="منفذ البلوتوث (COM):").pack(anchor="w")
        row = ttk.Frame(top)
        row.pack(fill="x")
        self.port_var = tk.StringVar()
        self.combo = ttk.Combobox(row, textvariable=self.port_var, state="readonly")
        self.combo.pack(side="left", fill="x", expand=True)
        ttk.Button(row, text="تحديث", command=self.refresh_ports).pack(side="left", padx=4)

        self.connect_btn = ttk.Button(top, text="اتصال", command=self.toggle_connect)
        self.connect_btn.pack(fill="x", pady=6)

        self.status = tk.StringVar(value="غير متصل")
        ttk.Label(top, textvariable=self.status).pack(anchor="w")

        # أزرار التحكم
        ctl = ttk.Frame(root, padding=10)
        ctl.pack(fill="both", expand=True)
        ttk.Button(ctl, text="قفل الهاتف", command=lambda: self.send("LOCK")).pack(fill="x", pady=4)

        vrow = ttk.Frame(ctl)
        vrow.pack(fill="x", pady=4)
        ttk.Button(vrow, text="صوت -", command=lambda: self.send("VOL_DOWN")).pack(side="left", fill="x", expand=True)
        ttk.Button(vrow, text="صوت +", command=lambda: self.send("VOL_UP")).pack(side="left", fill="x", expand=True)

        self.scale = tk.Scale(ctl, from_=0, to=15, orient="horizontal", label="مستوى الصوت")
        self.scale.pack(fill="x", pady=4)
        self.scale.bind("<ButtonRelease-1>", lambda e: self.send(f"SET:{self.scale.get()}"))

        self.refresh_ports()

    def refresh_ports(self):
        ports = [f"{p.device} - {p.description}" for p in serial.tools.list_ports.comports()]
        self.combo["values"] = ports
        if ports and not self.port_var.get():
            self.combo.current(0)

    def toggle_connect(self):
        if self.ser:
            self.ser.close()
            self.ser = None
            self.connect_btn.config(text="اتصال")
            self.status.set("غير متصل")
            return
        if not self.port_var.get():
            messagebox.showwarning("تنبيه", "اختر منفذ COM أولاً")
            return
        port = self.port_var.get().split(" - ")[0]
        self.status.set("جاري الاتصال...")
        self.root.update()
        try:
            self.ser = serial.Serial(port, timeout=3)
            self.connect_btn.config(text="قطع الاتصال")
            self.status.set("متصل")
            self.send("GET")
        except Exception as e:
            self.ser = None
            self.status.set("فشل الاتصال")
            messagebox.showerror("خطأ", f"تعذر فتح {port}\n{e}\n\nتأكد أن الخدمة تعمل على الموبايل.")

    def send(self, cmd):
        if not self.ser:
            messagebox.showwarning("تنبيه", "غير متصل")
            return
        try:
            self.ser.write((cmd + "\n").encode())
            reply = self.ser.readline().decode(errors="ignore").strip()
        except Exception as e:
            self.status.set("انقطع الاتصال")
            self.ser = None
            self.connect_btn.config(text="اتصال")
            messagebox.showerror("خطأ", str(e))
            return

        if reply.startswith("VOL:"):
            cur, mx = reply[4:].split("/")
            self.scale.config(to=int(mx))
            self.scale.set(int(cur))
            self.status.set(f"متصل - الصوت {cur}/{mx}")
        elif reply == "OK":
            self.status.set("تم")
        elif reply == "ERR:NO_ADMIN":
            messagebox.showwarning("تنبيه", "فعّل صلاحية قفل الشاشة من تطبيق الموبايل أولاً")
        elif reply:
            self.status.set(reply)
        else:
            self.status.set("لا يوجد رد من الموبايل")


if __name__ == "__main__":
    root = tk.Tk()
    App(root)
    root.mainloop()
