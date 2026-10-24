import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Scanner;

/**
 * Лабораторная работа 2, часть 1 (сложный вариант).
 * Строковый тип данных (класс StringBuffer): обратный порядок символов
 * и добавление одной строки в другую.
 * Результаты сохраняются в PostgreSQL и выгружаются в Excel (CSV).
 */
public class Main {
    public static void main(String[] args) {
        Laba a = new Laba();
        if (!a.podkl()) return;

        while (true) {
            System.out.println();
            System.out.println("========== МЕНЮ ==========");
            System.out.println("1. Вывести все таблицы из PostgreSQL");
            System.out.println("2. Создать таблицу в PostgreSQL");
            System.out.println("3. Изменить порядок символов строки на обратный, результат сохранить в PostgreSQL с последующим выводом в консоль");
            System.out.println("4. Добавить одну строку в другую, результат сохранить в PostgreSQL с последующим выводом в консоль");
            System.out.println("5. Сохранить все данные из PostgreSQL в Excel и вывести на экран");
            System.out.println("0. Выход");
            System.out.print("Ваш выбор: ");

            int vib = a.chitatVybor();
            try {
                switch (vib) {
                    case 0:
                        a.zakr();
                        System.out.println("Работа завершена");
                        return;
                    case 1: a.tabli(); break;
                    case 2: a.sozdTabl(); break;
                    case 3: a.reverse(); break;
                    case 4: a.dobavit(); break;
                    case 5: a.exel(); break;
                    default: System.out.println("Нет такого пункта меню, введите число от 0 до 5");
                }
            } catch (SQLException e) {
                System.out.println("Ошибка работы с базой данных: " + e.getMessage());
            }
        }
    }
}

class Laba {
    // Параметры подключения к PostgreSQL (при необходимости поменяйте под себя)
    static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    static final String USER = "postgres";
    static final String PASSWORD = "postgres";
    static final int MIN_DLINA = 50;

    Scanner scan = new Scanner(System.in);
    Connection conn;
    Statement stat;
    String tablename;                           // таблица, с которой сейчас работаем
    StringBuffer[] strok = new StringBuffer[2]; // две введённые строки

    // ---------- Подключение к БД ----------

    boolean podkl() {
        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            stat = conn.createStatement();
            System.out.println("Подключение к PostgreSQL прошло успешно");
            return true;
        } catch (SQLException e) {
            System.out.println("Не удалось подключиться к базе данных: " + e.getMessage());
            System.out.println("Проверьте, что PostgreSQL запущен и логин/пароль в классе Laba верные");
            return false;
        }
    }

    void zakr() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
        }
    }

    int chitatVybor() {
        if (!scan.hasNextLine()) return 0;
        String s = scan.nextLine().trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    StringBuffer chitatStroku(String text) {
        System.out.println(text);
        while (true) {
            String s = scan.nextLine();
            if (s.length() >= MIN_DLINA) return new StringBuffer(s);
            System.out.println("В строке " + s.length() + " символов, нужно не менее " + MIN_DLINA
                    + ". Введите ещё раз:");
        }
    }

    // ---------- 1. Вывод всех таблиц ----------

    void tabli() throws SQLException {
        ResultSet rs = stat.executeQuery("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name");
        System.out.println("\n=== Список всех таблиц в базе данных ===");
        int count = 0;
        while (rs.next()) {
            count++;
            System.out.println(count + ". " + rs.getString("table_name"));
        }
        rs.close();
        if (count == 0) System.out.println("Таблиц не найдено");
        else System.out.println("Всего таблиц: " + count);
    }

    // ---------- 2. Создание таблицы ----------

    void sozdTabl() throws SQLException {
        String name;
        while (true) {
            System.out.print("Введите название таблицы (латиница, цифры, _): ");
            name = scan.nextLine().trim().toLowerCase();
            if (name.matches("[a-z_][a-z0-9_]*")) break;
            System.out.println("Некорректное название таблицы");
        }
        tablename = name;
        stat.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablename + " ("
                + "id SERIAL PRIMARY KEY, "
                + "operaciya VARCHAR(50), "
                + "stroka1 TEXT, "
                + "stroka2 TEXT, "
                + "rezultat TEXT)");
        System.out.println("Таблица '" + tablename + "' создана (или уже существовала) и выбрана для работы");
    }

    // ---------- Подготовка строк перед операцией ----------
    // Если строки уже есть (введены в этом запуске или сохранены в таблице раньше),
    // показываем их и предлагаем ввести новые. Иначе просим ввести две строки.

    boolean podgotovka() throws SQLException {
        if (tablename == null) {
            System.out.println("Сначала создайте или выберите таблицу (пункт 2)");
            return false;
        }
        if (strok[0] == null) {
            ResultSet rs = stat.executeQuery("SELECT stroka1, stroka2 FROM " + tablename
                    + " WHERE operaciya = 'Ввод строк' ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                strok[0] = new StringBuffer(rs.getString("stroka1"));
                strok[1] = new StringBuffer(rs.getString("stroka2"));
            }
            rs.close();
        }

        boolean novye = true;
        if (strok[0] != null) {
            System.out.println("Ранее введённые строки:");
            System.out.println("1) " + strok[0]);
            System.out.println("2) " + strok[1]);
            System.out.print("Ввести новые строки? (д/н, Enter - нет): ");
            String otv = scan.nextLine().trim().toLowerCase();
            novye = otv.startsWith("д") || otv.startsWith("y");
        }
        if (novye) {
            strok[0] = chitatStroku("Введите первую строку (не менее " + MIN_DLINA + " символов):");
            strok[1] = chitatStroku("Введите вторую строку (не менее " + MIN_DLINA + " символов):");
            sohranit("Ввод строк", "Строки введены");
        }
        return true;
    }

    // ---------- 3. Обратный порядок символов ----------

    void reverse() throws SQLException {
        if (!podgotovka()) return;
        // reverse() меняет сам объект, поэтому работаем с копиями, исходные строки не трогаем
        StringBuffer r1 = new StringBuffer(strok[0]).reverse();
        StringBuffer r2 = new StringBuffer(strok[1]).reverse();
        sohranit("Обратный порядок", "Строка 1: " + r1 + " | Строка 2: " + r2);
    }

    // ---------- 4. Добавление одной строки в другую ----------

    void dobavit() throws SQLException {
        if (!podgotovka()) return;
        System.out.println("Куда добавить строку?");
        System.out.println("1 - вторую строку в конец первой (append)");
        System.out.println("2 - первую строку в конец второй (append)");
        System.out.println("3 - вторую строку в начало первой (insert)");
        System.out.print("Ваш выбор (Enter - 1): ");
        String v = scan.nextLine().trim();

        StringBuffer res;
        String opis;
        if (v.equals("2")) {
            res = new StringBuffer(strok[1]).append(strok[0]);
            opis = "строка 1 добавлена в конец строки 2";
        } else if (v.equals("3")) {
            res = new StringBuffer(strok[0]).insert(0, strok[1]);
            opis = "строка 2 вставлена в начало строки 1";
        } else {
            res = new StringBuffer(strok[0]).append(strok[1]);
            opis = "строка 2 добавлена в конец строки 1";
        }
        sohranit("Добавление строки", opis + " (длина " + res.length() + "): " + res);
    }

    // ---------- Сохранение в БД и вывод сохранённой записи ----------

    void sohranit(String op, String rezultat) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("INSERT INTO " + tablename
                + " (operaciya, stroka1, stroka2, rezultat) VALUES (?,?,?,?) RETURNING id");
        ps.setString(1, op);
        ps.setString(2, strok[0].toString());
        ps.setString(3, strok[1].toString());
        ps.setString(4, rezultat);
        ResultSet rs = ps.executeQuery();
        rs.next();
        int id = rs.getInt(1);
        rs.close();
        ps.close();
        System.out.println("Результат сохранён в таблицу '" + tablename + "' (id = " + id + ")");

        // Читаем сохранённую запись обратно из БД и выводим в консоль
        PreparedStatement sel = conn.prepareStatement("SELECT * FROM " + tablename + " WHERE id = ?");
        sel.setInt(1, id);
        ResultSet r = sel.executeQuery();
        if (r.next()) {
            System.out.println("\n--- " + r.getString("operaciya") + " (данные из БД) ---");
            System.out.println("Строка 1:  " + r.getString("stroka1"));
            System.out.println("Строка 2:  " + r.getString("stroka2"));
            System.out.println("Результат: " + r.getString("rezultat"));
        }
        r.close();
        sel.close();
    }

    // ---------- 5. Экспорт в Excel ----------

    void exel() throws SQLException {
        if (tablename == null) {
            System.out.println("Сначала создайте или выберите таблицу (пункт 2)");
            return;
        }
        ResultSet rs = stat.executeQuery("SELECT * FROM " + tablename + " ORDER BY id");
        File file = new File(papkaDlyaFaila(), tablename + ".csv");

        System.out.println("\n=== Содержимое таблицы '" + tablename + "' ===");
        int count = 0;
        // CSV с BOM и разделителем ';' - Excel открывает его с правильной кодировкой
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write('﻿');
            w.write("id;Операция;Строка 1;Строка 2;Результат\n");
            while (rs.next()) {
                count++;
                String[] row = {
                        rs.getString("id"),
                        rs.getString("operaciya"),
                        rs.getString("stroka1"),
                        rs.getString("stroka2"),
                        rs.getString("rezultat")
                };
                System.out.println("[" + row[0] + "] " + row[1]);
                System.out.println("    Строка 1:  " + row[2]);
                System.out.println("    Строка 2:  " + row[3]);
                System.out.println("    Результат: " + row[4]);
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) w.write(';');
                    w.write(csv(row[i]));
                }
                w.write('\n');
            }
        } catch (IOException e) {
            System.out.println("Ошибка при создании файла: " + e.getMessage());
            return;
        } finally {
            rs.close();
        }

        if (count == 0) System.out.println("Таблица пуста");
        System.out.println("\nДанные сохранены в Excel-файл: " + file.getAbsolutePath() + " (записей: " + count + ")");
        otkrytFail(file);
    }

    // Строки могут содержать ';' и кавычки - экранируем по правилам CSV
    String csv(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    File papkaDlyaFaila() {
        File desktop = new File(System.getProperty("user.home"), "Desktop");
        if (desktop.isDirectory()) return desktop;
        File oneDrive = new File(System.getProperty("user.home"), "OneDrive" + File.separator + "Desktop");
        if (oneDrive.isDirectory()) return oneDrive;
        return new File(System.getProperty("user.dir"));
    }

    void otkrytFail(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
                System.out.println("Файл открыт в Excel");
                return;
            }
        } catch (Exception ignored) {
        }
        System.out.println("Откройте файл в Excel вручную");
    }
}
